/*
 * Copyright 2019 is-land
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.island.ohara.it.agent

import java.io.File
import java.util.concurrent.ExecutionException

import com.island.ohara.agent.ClusterState
import com.island.ohara.agent.docker.{ContainerState, DockerClient}
import com.island.ohara.client.configurator.v0.NodeApi.Node
import com.island.ohara.client.configurator.v0.{ZookeeperApi, _}
import com.island.ohara.common.data.{Row, Serializer}
import com.island.ohara.common.setting.{ObjectKey, TopicKey}
import com.island.ohara.common.util.{CommonUtils, Releasable}
import com.island.ohara.configurator.Configurator
import com.island.ohara.it.IntegrationTest
import com.island.ohara.kafka.Producer
import com.typesafe.scalalogging.Logger
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException
import org.junit.{After, Before, Test}
import org.scalatest.Matchers

import scala.concurrent.ExecutionContext.Implicits.global

abstract class BasicTests4StreamApp extends IntegrationTest with Matchers {

  private[this] val log = Logger(classOf[BasicTests4StreamApp])

  /**
    * we need to export port to enable remote node download jar from this node
    */
  private[this] val portKey = "ohara.it.port"

  /**
    * we need to export hostname to enable remote node download jar from this node
    */
  private[this] val hostnameKey: String = "ohara.it.hostname"

  private[this] val invalidHostname = "unknown"
  private[this] val invalidPort = 0
  private[this] val publicHostname: String = sys.env.getOrElse(hostnameKey, invalidHostname)
  private[this] val publicPort = sys.env.get(portKey).fold(invalidPort)(_.toInt)

  private[this] var nodeCache: Seq[Node] = _
  private[this] var nameHolder: ClusterNameHolder = _

  protected def createNodes(): Seq[Node]

  protected def createNameHolder(nodeCache: Seq[Node]): ClusterNameHolder

  protected def createConfigurator(nodeCache: Seq[Node], hostname: String, port: Int): Configurator

  private[this] var configurator: Configurator = _

  private[this] var zkApi: ZookeeperApi.Access = _
  private[this] var bkApi: BrokerApi.Access = _
  private[this] var wkApi: WorkerApi.Access = _
  private[this] var containerApi: ContainerApi.Access = _
  private[this] var topicApi: TopicApi.Access = _
  private[this] var jarApi: FileInfoApi.Access = _

  private[this] var access: StreamApi.Access = _
  private[this] var bkName: String = _
  private[this] var wkName: String = _
  private[this] var brokerConnProps: String = _
  private[this] val instances = 1

  private[this] def waitStopFinish(clusterName: String): Unit = {
    await(() => {
      // In configurator mode: clusters() will return the "stopped list" in normal case
      // In collie mode: clusters() will return the "cluster list except stop one" in normal case
      // we should consider these two cases by case...
      val clusters = result(access.list())
      !clusters.map(_.name).contains(clusterName) || clusters.find(_.name == clusterName).get.state.isEmpty
    })
  }

  @Before
  def setup(): Unit = {
    nodeCache = createNodes()
    if (nodeCache.isEmpty || publicPort == invalidPort || publicHostname == invalidHostname) {
      skipTest(
        s"${CollieTestUtils.key}, $portKey and $hostnameKey don't exist so all tests in BasicTests4StreamApp are ignored"
      )
    } else {
      nameHolder = createNameHolder(nodeCache)
      bkName = nameHolder.generateClusterName()
      wkName = nameHolder.generateClusterName()
      configurator = createConfigurator(nodeCache, publicHostname, publicPort)
      zkApi = ZookeeperApi.access.hostname(configurator.hostname).port(configurator.port)
      bkApi = BrokerApi.access.hostname(configurator.hostname).port(configurator.port)
      wkApi = WorkerApi.access.hostname(configurator.hostname).port(configurator.port)
      containerApi = ContainerApi.access.hostname(configurator.hostname).port(configurator.port)
      topicApi = TopicApi.access.hostname(configurator.hostname).port(configurator.port)
      jarApi = FileInfoApi.access.hostname(configurator.hostname).port(configurator.port)
      access = StreamApi.access.hostname(configurator.hostname).port(configurator.port)
      val nodeApi = NodeApi.access.hostname(configurator.hostname).port(configurator.port)
      // add all available nodes
      nodeCache.foreach { node =>
        result(
          nodeApi.request.hostname(node.hostname).port(node._port).user(node._user).password(node._password).create()
        )
      }
      val nodes = result(nodeApi.list())
      nodes.size shouldBe nodeCache.size
      nodeCache.forall(node => nodes.map(_.name).contains(node.name)) shouldBe true

      // create zookeeper cluster
      log.info("create zkCluster...start")
      val zkCluster = result(
        zkApi.request.name(nameHolder.generateClusterName()).nodeNames(nodeCache.take(1).map(_.name).toSet).create()
      )
      result(zkApi.start(zkCluster.name))
      assertCluster(() => result(zkApi.list()), zkCluster.name)
      await(() => {
        val containers = result(containerApi.get(zkCluster.name).map(_.flatMap(_.containers)))
        containers.nonEmpty && containers.map(_.state).forall(_.equals(ContainerState.RUNNING.name))
      })
      log.info("create zkCluster...done")

      // create broker cluster
      log.info("create bkCluster...start")
      val bkCluster = result(
        bkApi.request
          .name(bkName)
          .zookeeperClusterName(zkCluster.name)
          .nodeNames(nodeCache.take(1).map(_.name).toSet)
          .create())
      result(bkApi.start(bkCluster.name))
      assertCluster(() => result(bkApi.list()), bkCluster.name)
      await(() => {
        val containers = result(containerApi.get(bkCluster.name).map(_.flatMap(_.containers)))
        containers.nonEmpty && containers.map(_.state).forall(_.equals(ContainerState.RUNNING.name))
      })
      log.info("create bkCluster...done")
      brokerConnProps = bkCluster.connectionProps

      // create worker cluster
      log.info("create wkCluster...start")
      result(
        wkApi.request
          .name(wkName)
          .brokerClusterName(bkCluster.name)
          .nodeNames(nodeCache.take(instances).map(_.name).toSet)
          .create()
          .flatMap(wk => wkApi.start(wk.name)))
      assertCluster(() => result(wkApi.list()), wkName)
      log.info("create wkCluster...done")
    }
  }

  @Test
  def testFailedClusterRemoveGracefully(): Unit = {

    // create fake jar
    val jar = CommonUtils.createTempJar("fake")

    // upload streamApp jar
    val jarInfo = result(jarApi.request.group(wkName).file(jar).upload())

    // create streamApp properties
    val stream = result(
      access.request.name(CommonUtils.randomString(10)).jarKey(ObjectKey.of(jarInfo.group, jarInfo.name)).create())

    // create topic
    val topic1 = result(topicApi.request.name("bar").brokerClusterName(bkName).create())
    result(topicApi.start(topic1.key))
    val topic2 = result(topicApi.request.name("foo").brokerClusterName(bkName).create())
    result(topicApi.start(topic2.key))

    // update streamApp properties
    val properties = result(
      access.request.name(stream.name).from(Set(topic1.topicNameOnKafka)).to(Set(topic2.topicNameOnKafka)).update()
    )
    properties.from.isEmpty shouldBe false
    properties.to.isEmpty shouldBe false
    properties.instances shouldBe instances
    properties.state shouldBe None
    properties.error shouldBe None

    // start streamApp
    result(access.start(stream.name))
    await(() => result(access.get(stream.name)).state.isDefined)

    // get the actually container names
    val map = nodeCache.map { node =>
      if (configurator.k8sClient.isDefined) {
        val client = configurator.k8sClient.get
        node -> result(client.containers()).map(_.name).filter(name => name.contains(properties.name))
      } else {
        val client =
          DockerClient.builder.hostname(node.hostname).port(node._port).user(node._user).password(node._password).build
        try node -> client.containerNames().filter(name => name.contains(properties.name))
        finally client.close()
      }
    }

    // we only have one instance, container exited means cluster dead (the state here uses container state is ok
    // since we use the same name for cluster state
    await(() => {
      val res = result(access.get(stream.name))
      res.state.isDefined && res.state.get == ClusterState.FAILED.name &&
      // only 1 instance, dead nodes are equal to all nodes
      res.deadNodes == res.nodeNames &&
      res.nodeNames.size == 1
    })

    // stop and remove failed cluster gracefully
    result(access.stop(stream.name))
    waitStopFinish(stream.name)

    // wait streamApp until removed actually
    await(() => {
      val res = result(access.get(stream.name))
      res.state.isEmpty
    })

    // check the containers are all removed
    map.foreach {
      case (node, containers) =>
        if (configurator.k8sClient.isDefined) {
          val client = configurator.k8sClient.get
          containers.foreach(container => !result(client.containers()).map(_.name).contains(container) shouldBe true)
        } else {
          val client =
            DockerClient.builder
              .hostname(node.hostname)
              .port(node._port)
              .user(node._user)
              .password(node._password)
              .build
          try containers.foreach(container => client.nonExist(container) shouldBe true)
          finally client.close()
        }
    }
  }

  @Test
  def testRunSimpleStreamApp(): Unit = {
    val from = TopicKey.of("default", "fromTopic")
    val to = TopicKey.of("default", "toTopic")
    val jar = new File(CommonUtils.path(System.getProperty("user.dir"), "build", "libs", "ohara-streamapp.jar"))

    // we make sure the broker cluster exists again (for create topic)
    assertCluster(() => result(bkApi.list()), bkName)

    // create topic
    val topic1 = result(topicApi.request.key(from).brokerClusterName(bkName).create())
    result(topicApi.start(topic1.key))
    val topic2 = result(topicApi.request.key(to).brokerClusterName(bkName).create())
    result(topicApi.start(topic2.key))

    // upload streamApp jar
    val jarInfo = result(jarApi.request.group(wkName).file(jar).upload())
    jarInfo.name shouldBe "ohara-streamapp.jar"
    jarInfo.group shouldBe wkName

    // create streamApp properties
    val stream = result(
      access.request.name(CommonUtils.randomString(10)).jarKey(ObjectKey.of(jarInfo.group, jarInfo.name)).create())

    // update streamApp properties
    val properties = result(
      access.request
        .name(stream.name)
        .from(Set(topic1.topicNameOnKafka))
        .to(Set(topic2.topicNameOnKafka))
        .instances(instances)
        .update()
    )
    properties.from shouldBe Set(topic1.topicNameOnKafka)
    properties.to shouldBe Set(topic2.topicNameOnKafka)
    properties.instances shouldBe instances
    properties.state shouldBe None
    properties.error shouldBe None

    // get streamApp property (cluster not create yet, hence no state)
    val getProperties = result(access.get(stream.name))
    getProperties.from shouldBe Set(topic1.topicNameOnKafka)
    getProperties.to shouldBe Set(topic2.topicNameOnKafka)
    getProperties.instances shouldBe instances
    getProperties.state shouldBe None
    getProperties.error shouldBe None

    // start streamApp
    result(access.start(stream.name))
    await(() => {
      val res = result(access.get(stream.name))
      res.state.isDefined && res.state.get == ClusterState.RUNNING.name
    })

    val res1 = result(access.get(stream.name))
    res1.name shouldBe stream.name
    res1.error shouldBe None
    // save the cluster name to cache
    nameHolder.addClusterName(res1.name)

    // check the cluster has the metrics data (each stream cluster has two metrics : IN_TOPIC and OUT_TOPIC)
    await(() => result(access.get(stream.name)).metrics.meters.nonEmpty)
    result(access.get(stream.name)).metrics.meters.size shouldBe 2

    // write some data into topic
    val producer = Producer
      .builder[Row, Array[Byte]]()
      .connectionProps(brokerConnProps)
      .allAcks()
      .keySerializer(Serializer.ROW)
      .valueSerializer(Serializer.BYTES)
      .build()
    try {
      await(
        () => {
          try producer
            .sender()
            .key(Row.EMPTY)
            .value(Array.emptyByteArray)
            .topicName(topic1.topicNameOnKafka)
            .send()
            .get()
            .topicName() == topic1.topicNameOnKafka
          catch {
            case e: ExecutionException =>
              e.getCause match {
                case _: UnknownTopicOrPartitionException => false
              }
          }
        }
      )
    } finally producer.close()

    // wait until the metrics cache data update
    await(() => result(access.get(stream.name)).metrics.meters.forall(_.value > 0))

    // check the metrics data again
    val metrics = result(access.get(stream.name)).metrics.meters
    metrics.foreach { metric =>
      metric.document should include("the number of rows")
      metric.value shouldBe 1D
    }

    //stop streamApp
    result(access.stop(stream.name))
    waitStopFinish(stream.name)
    result(access.get(stream.name)).state.isEmpty shouldBe true

    // after stop streamApp, property should still exist
    result(access.get(stream.name)).name shouldBe stream.name
  }

  @After
  def cleanUp(): Unit = {
    Releasable.close(nameHolder)
    Releasable.close(configurator)
  }
}
