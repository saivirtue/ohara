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
import com.island.ohara.agent.docker.DockerClient
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

  private[this] val invalidHostname = "unknown"
  private[this] val invalidPort = 0
  private[this] val hostname: String = publicHostname.getOrElse(invalidHostname)
  private[this] val port = publicPort.getOrElse(invalidPort)

  protected val nodes: Seq[Node]
  protected val nameHolder: ClusterNameHolder

  protected def createConfigurator(hostname: String, port: Int): Configurator

  private[this] var configurator: Configurator = _

  private[this] var zkApi: ZookeeperApi.Access = _
  private[this] var bkApi: BrokerApi.Access = _
  private[this] var containerApi: ContainerApi.Access = _
  private[this] var topicApi: TopicApi.Access = _
  private[this] var jarApi: FileInfoApi.Access = _

  private[this] var access: StreamApi.Access = _
  private[this] var bkKey: ObjectKey = _
  private[this] var brokerConnProps: String = _
  private[this] val instances = 1

  private[this] def waitStopFinish(objectKey: ObjectKey): Unit = {
    await(() => {
      // In configurator mode: clusters() will return the "stopped list" in normal case
      // In collie mode: clusters() will return the "cluster list except stop one" in normal case
      // we should consider these two cases by case...
      val clusters = result(access.list())
      !clusters.map(_.key).contains(objectKey) || clusters.find(_.key == objectKey).get.state.isEmpty
    })
  }

  @Before
  def setup(): Unit = {
    if (nodes.isEmpty || port == invalidPort || hostname == invalidHostname) {
      skipTest("public hostname and public port must exist so all tests in BasicTests4StreamApp are ignored")
    } else {
      configurator = createConfigurator(hostname, port)
      zkApi = ZookeeperApi.access.hostname(configurator.hostname).port(configurator.port)
      bkApi = BrokerApi.access.hostname(configurator.hostname).port(configurator.port)
      containerApi = ContainerApi.access.hostname(configurator.hostname).port(configurator.port)
      topicApi = TopicApi.access.hostname(configurator.hostname).port(configurator.port)
      jarApi = FileInfoApi.access.hostname(configurator.hostname).port(configurator.port)
      access = StreamApi.access.hostname(configurator.hostname).port(configurator.port)
      val nodeApi = NodeApi.access.hostname(configurator.hostname).port(configurator.port)
      // add all available nodes
      nodes.foreach { node =>
        result(
          nodeApi.request.hostname(node.hostname).port(node._port).user(node._user).password(node._password).create()
        )
      }
      nodes.size shouldBe nodes.size
      nodes.forall(node => nodes.map(_.name).contains(node.name)) shouldBe true

      // create zookeeper cluster
      log.info("create zkCluster...start")
      val zkCluster = result(
        zkApi.request.name(nameHolder.generateClusterName()).nodeNames(nodes.take(1).map(_.name).toSet).create()
      )
      result(zkApi.start(zkCluster.key))
      assertCluster(() => result(zkApi.list()),
                    () => result(containerApi.get(zkCluster.key).map(_.flatMap(_.containers))),
                    zkCluster.name)
      log.info("create zkCluster...done")

      // create broker cluster
      log.info("create bkCluster...start")
      val bkCluster = result(
        bkApi.request
          .name(nameHolder.generateClusterName())
          .zookeeperClusterKey(zkCluster.key)
          .nodeNames(nodes.take(1).map(_.name).toSet)
          .create())
      bkKey = bkCluster.key
      result(bkApi.start(bkCluster.key))
      assertCluster(() => result(bkApi.list()),
                    () => result(containerApi.get(bkCluster.key).map(_.flatMap(_.containers))),
                    bkCluster.name)
      log.info("create bkCluster...done")
      brokerConnProps = bkCluster.connectionProps

    }
  }

  /**
    * I felt embarrassed about this test case as it produces a error to StreamApp via our public APIs.
    * TODO: Personally, We should protect our running cluster ... by chia
    */
  @Test
  def testFailedClusterRemoveGracefully(): Unit = {
    val jar = new File(CommonUtils.path(System.getProperty("user.dir"), "build", "libs", "ohara-streamapp.jar"))

    // upload streamApp jar
    val jarInfo = result(jarApi.request.file(jar).upload())

    // create streamApp properties
    val stream = result(
      access.request
        .name(nameHolder.generateClusterName())
        .jarKey(jarInfo.key)
        .brokerClusterKey(bkKey)
        .instances(instances)
        .create())

    // create topic
    val topic1 = result(topicApi.request.name("bar").brokerClusterKey(bkKey).create())
    result(topicApi.start(topic1.key))
    val topic2 = result(topicApi.request.name("foo").brokerClusterKey(bkKey).create())
    result(topicApi.start(topic2.key))

    // update streamApp properties
    val properties = result(
      access.request.name(stream.name).fromTopicKey(topic1.key).toTopicKey(topic2.key).update()
    )
    properties.fromTopicKeys shouldBe Set(topic1.key)
    properties.toTopicKeys shouldBe Set(topic2.key)
    properties.nodeNames.size shouldBe instances
    properties.state shouldBe None
    properties.error shouldBe None

    result(access.start(stream.key))
    await(() => result(access.get(stream.key)).state.isDefined)

    // get the actually container names
    val map = nodes.map { node =>
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

    // remove the topic to make streamapp fail
    result(topicApi.stop(topic1.key))

    // we only have one instance, container exited means cluster dead (the state here uses container state is ok
    // since we use the same name for cluster state
    await(() => {
      val res = result(access.get(stream.key))
      res.state.isDefined && res.state.get == ClusterState.FAILED.name &&
      // only 1 instance, dead nodes are equal to all nodes
      res.deadNodes == res.nodeNames &&
      res.nodeNames.size == 1
    })

    // stop and remove failed cluster gracefully
    result(access.stop(stream.key))
    waitStopFinish(stream.key)

    // wait streamApp until removed actually
    await(() => {
      val res = result(access.get(stream.key))
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
    val from = TopicKey.of("default", CommonUtils.randomString(5))
    val to = TopicKey.of("default", CommonUtils.randomString(5))
    val jar = new File(CommonUtils.path(System.getProperty("user.dir"), "build", "libs", "ohara-streamapp.jar"))

    // jar should be parse-able
    log.info(s"[testRunSimpleStreamApp] get definition from $jar")
    val definition = result(configurator.clusterCollie.streamCollie.loadDefinition(jar.toURI.toURL))
    definition.isDefined shouldBe true
    definition.get.className shouldBe "com.island.ohara.it.streamapp.DumbStreamApp"
    log.info(s"[testRunSimpleStreamApp] get definition from $jar...done")
    // we make sure the broker cluster exists again (for create topic)
    assertCluster(() => result(bkApi.list()),
                  () => result(containerApi.get(bkKey).map(_.flatMap(_.containers))),
                  bkKey.name())
    log.info(s"[testRunSimpleStreamApp] broker cluster [$bkKey] assert...done")
    // create topic
    val topic1 = result(topicApi.request.key(from).brokerClusterKey(bkKey).create())
    result(topicApi.start(topic1.key))
    val topic2 = result(topicApi.request.key(to).brokerClusterKey(bkKey).create())
    result(topicApi.start(topic2.key))
    log.info(s"[testRunSimpleStreamApp] topic creation [$topic1,$topic2]...done")

    // upload streamApp jar
    val jarInfo = result(jarApi.request.file(jar).upload())
    jarInfo.name shouldBe "ohara-streamapp.jar"
    log.info(s"[testRunSimpleStreamApp] upload jar [$jarInfo]...done")

    // create streamApp properties
    val stream = result(
      access.request.name(nameHolder.generateClusterName()).jarKey(jarInfo.key).brokerClusterKey(bkKey).create())
    log.info(s"[testRunSimpleStreamApp] stream properties creation [$stream]...done")

    // update streamApp properties
    val properties = result(
      access.request.name(stream.name).fromTopicKey(topic1.key).toTopicKey(topic2.key).instances(instances).update()
    )
    properties.fromTopicKeys shouldBe Set(topic1.key)
    properties.toTopicKeys shouldBe Set(topic2.key)
    properties.nodeNames.size shouldBe instances
    properties.state shouldBe None
    properties.error shouldBe None
    log.info(s"[testRunSimpleStreamApp] stream properties update [$properties]...done")

    // get streamApp property (cluster not create yet, hence no state)
    val getProperties = result(access.get(stream.key))
    getProperties.fromTopicKeys shouldBe Set(topic1.key)
    getProperties.toTopicKeys shouldBe Set(topic2.key)
    getProperties.nodeNames.size shouldBe instances
    getProperties.state shouldBe None
    getProperties.error shouldBe None

    // start streamApp
    log.info(s"[testRunSimpleStreamApp] stream start [${stream.key}]")
    result(access.start(stream.key))
    await(() => {
      val res = result(access.get(stream.key))
      res.state.isDefined && res.state.get == ClusterState.RUNNING.name
    })
    log.info(s"[testRunSimpleStreamApp] stream start [${stream.key}]...done")

    val res1 = result(access.get(stream.key))
    res1.key shouldBe stream.key
    res1.error shouldBe None

    // check the cluster has the metrics data (each stream cluster has two metrics : IN_TOPIC and OUT_TOPIC)
    await(() => result(access.get(stream.key)).metrics.meters.nonEmpty)
    result(access.get(stream.key)).metrics.meters.size shouldBe 2

    // write some data into topic
    val producer = Producer
      .builder()
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
    await(() => result(access.get(stream.key)).metrics.meters.forall(_.value > 0))

    // check the metrics data again
    val metrics = result(access.get(stream.key)).metrics.meters
    metrics.foreach { metric =>
      metric.document should include("the number of rows")
      metric.value shouldBe 1D
    }

    await(() => result(topicApi.get(from)).metrics.meters.nonEmpty)
    await(() => result(topicApi.get(to)).metrics.meters.nonEmpty)

    //stop streamApp
    result(access.stop(stream.key))
    waitStopFinish(stream.key)
    result(access.get(stream.key)).state.isEmpty shouldBe true

    // after stop streamApp, property should still exist
    result(access.get(stream.key)).name shouldBe stream.name
  }

  @Test
  def testDeadNodes(): Unit = if (nodes.size < 2) skipTest(s"requires two nodes at least")
  else {
    val from = TopicKey.of("default", CommonUtils.randomString(5))
    val to = TopicKey.of("default", CommonUtils.randomString(5))
    val jar = new File(CommonUtils.path(System.getProperty("user.dir"), "build", "libs", "ohara-streamapp.jar"))
    // create topic
    val topic1 = result(topicApi.request.key(from).brokerClusterKey(bkKey).create())
    result(topicApi.start(topic1.key))
    val topic2 = result(topicApi.request.key(to).brokerClusterKey(bkKey).create())
    result(topicApi.start(topic2.key))

    // upload streamApp jar
    val jarInfo = result(jarApi.request.file(jar).upload())
    jarInfo.name shouldBe "ohara-streamapp.jar"

    // create streamApp properties
    val stream = result(
      access.request
        .name(nameHolder.generateClusterName())
        .jarKey(ObjectKey.of(jarInfo.group, jarInfo.name))
        .brokerClusterKey(bkKey)
        .nodeNames(nodes.map(_.hostname).toSet)
        .fromTopicKey(topic1.key)
        .toTopicKey(topic2.key)
        .create())

    // start streamApp
    result(access.start(stream.key))
    await(() => {
      val res = result(access.get(stream.key))
      res.state.isDefined && res.state.get == ClusterState.RUNNING.name
    })

    result(access.get(stream.key)).nodeNames shouldBe nodes.map(_.hostname).toSet
    result(access.get(stream.key)).deadNodes shouldBe Set.empty

    // remove a container directly
    val aliveNodes: Set[Node] = nodes.slice(1, nodes.size).toSet
    val deadNodes = nodes.toSet -- aliveNodes
    nameHolder.release(
      clusterNames = Set(stream.name),
      // remove the container from first node
      excludedNodes = aliveNodes.map(_.hostname)
    )

    result(access.get(stream.key)).state should not be None

    await { () =>
      val cluster = result(access.get(stream.key))
      cluster.nodeNames == nodes.map(_.hostname).toSet &&
      cluster.deadNodes == deadNodes.map(_.hostname)
    }
  }

  @After
  def cleanUp(): Unit = {
    Releasable.close(nameHolder)
    Releasable.close(configurator)
  }
}
