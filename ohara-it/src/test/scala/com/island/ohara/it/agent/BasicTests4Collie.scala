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

import java.time.Duration
import java.util.concurrent.TimeUnit

import com.island.ohara.agent.docker.ContainerState
import com.island.ohara.client.configurator.v0.BrokerApi.BrokerClusterInfo
import com.island.ohara.client.configurator.v0.ContainerApi.ContainerInfo
import com.island.ohara.client.configurator.v0.NodeApi.Node
import com.island.ohara.client.configurator.v0.WorkerApi.WorkerClusterInfo
import com.island.ohara.client.configurator.v0.ZookeeperApi.ZookeeperClusterInfo
import com.island.ohara.client.kafka.WorkerClient
import com.island.ohara.common.data.Serializer
import com.island.ohara.common.util.CommonUtils
import com.island.ohara.it.IntegrationTest
import com.island.ohara.common.exception.{OharaExecutionException, OharaTimeoutException}
import com.island.ohara.kafka.{BrokerClient, Consumer, Producer}
import com.island.ohara.metrics.BeanChannel
import com.typesafe.scalalogging.Logger
import org.apache.kafka.common.errors.{InvalidReplicationFactorException, UnknownTopicOrPartitionException}
import org.junit.Test
import org.scalatest.Matchers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionException, Future}

/**
  * This abstract class extracts the "required" information of running tests on true env.
  * All checks are verified in this class but we do run all test cases on different test in order to avoid
  * slow test cases run by single test jvm.
  *
  * NOTED: this test will forward random ports so it would be better to "close" firewall of remote node.
  */
abstract class BasicTests4Collie extends IntegrationTest with Matchers {
  private[this] val log = Logger(classOf[BasicTests4Collie])
  private[this] val numberOfClusters = 2
  protected val nodeCache: Seq[Node]

  //--------------------------------------------------[zk operations]--------------------------------------------------//
  protected def zk_exist(clusterName: String): Future[Boolean]
  protected def zk_create(clusterName: String,
                          clientPort: Int,
                          electionPort: Int,
                          peerPort: Int,
                          nodeNames: Set[String]): Future[ZookeeperClusterInfo]
  protected def zk_start(clusterName: String): Future[Unit]
  protected def zk_stop(clusterName: String): Future[Unit]
  protected def zk_cluster(clusterName: String): Future[ZookeeperClusterInfo] =
    zk_clusters().map(_.find(_.name == clusterName).get)
  protected def zk_clusters(): Future[Seq[ZookeeperClusterInfo]]
  protected def zk_logs(clusterName: String): Future[Seq[String]]
  protected def zk_containers(clusterName: String): Future[Seq[ContainerInfo]]
  protected def zk_delete(clusterName: String): Future[Unit]

  //--------------------------------------------------[bk operations]--------------------------------------------------//
  protected def bk_exist(clusterName: String): Future[Boolean]
  protected def bk_create(clusterName: String,
                          clientPort: Int,
                          exporterPort: Int,
                          jmxPort: Int,
                          zkClusterName: String,
                          nodeNames: Set[String]): Future[BrokerClusterInfo]
  protected def bk_start(clusterName: String): Future[Unit]
  protected def bk_stop(clusterName: String): Future[Unit]
  protected def bk_cluster(clusterName: String): Future[BrokerClusterInfo] =
    bk_clusters().map(_.find(_.name == clusterName).get)
  protected def bk_clusters(): Future[Seq[BrokerClusterInfo]]
  protected def bk_logs(clusterName: String): Future[Seq[String]]
  protected def bk_containers(clusterName: String): Future[Seq[ContainerInfo]]
  protected def bk_delete(clusterName: String): Future[Unit]
  protected def bk_addNode(clusterName: String, nodeName: String): Future[BrokerClusterInfo]
  protected def bk_removeNode(clusterName: String, nodeName: String): Future[Unit]

  //--------------------------------------------------[wk operations]--------------------------------------------------//
  protected def wk_exist(clusterName: String): Future[Boolean]
  protected def wk_create(clusterName: String,
                          clientPort: Int,
                          jmxPort: Int,
                          bkClusterName: String,
                          nodeNames: Set[String]): Future[WorkerClusterInfo]
  protected def wk_create(clusterName: String,
                          clientPort: Int,
                          jmxPort: Int,
                          groupId: String,
                          configTopicName: String,
                          statusTopicName: String,
                          offsetTopicName: String,
                          bkClusterName: String,
                          nodeNames: Set[String]): Future[WorkerClusterInfo]
  protected def wk_start(clusterName: String): Future[Unit]
  protected def wk_stop(clusterName: String): Future[Unit]
  protected def wk_cluster(clusterName: String): Future[WorkerClusterInfo] =
    wk_clusters().map(_.find(_.name == clusterName).get)
  protected def wk_clusters(): Future[Seq[WorkerClusterInfo]]
  protected def wk_logs(clusterName: String): Future[Seq[String]]
  protected def wk_containers(clusterName: String): Future[Seq[ContainerInfo]]
  protected def wk_delete(clusterName: String): Future[Unit]
  protected def wk_addNode(clusterName: String, nodeName: String): Future[WorkerClusterInfo]
  protected def wk_removeNode(clusterName: String, nodeName: String): Future[Unit]

  private[this] def cleanZookeeper(clusterName: String): Future[Unit] = {
    result(zk_stop(clusterName))
    await(() => {
      // In configurator mode: clusters() will return the "stopped list" in normal case
      // In collie mode: clusters() will return the "cluster list except stop one" in normal case
      // we should consider these two cases by case...
      val clusters = result(zk_clusters())
      !clusters.map(_.name).contains(clusterName) || clusters.find(_.name == clusterName).get.state.isEmpty
    })
    // the cluster is stopped actually, delete the data
    zk_delete(clusterName)
  }

  private[this] def cleanBroker(clusterName: String): Future[Unit] = {
    result(bk_stop(clusterName))
    await(() => {
      // In configurator mode: clusters() will return the "stopped list" in normal case
      // In collie mode: clusters() will return the "cluster list except stop one" in normal case
      // we should consider these two cases by case...
      val clusters = result(bk_clusters())
      !clusters.map(_.name).contains(clusterName) || clusters.find(_.name == clusterName).get.state.isEmpty
    })
    // the cluster is stopped actually, delete the data
    bk_delete(clusterName)
  }

  private[this] def cleanWorker(clusterName: String): Future[Unit] = {
    result(wk_stop(clusterName))
    await(() => {
      // In configurator mode: clusters() will return the "stopped list" in normal case
      // In collie mode: clusters() will return the "cluster list except stop one" in normal case
      // we should consider these two cases by case...
      val clusters = result(wk_clusters())
      !clusters.map(_.name).contains(clusterName) || clusters.find(_.name == clusterName).get.state.isEmpty
    })
    // the cluster is stopped actually, delete the data
    wk_delete(clusterName)
  }

  /**
    * used to debug...
    */
  protected val cleanup: Boolean = true

  protected def generateClusterName(): String

  @Test
  def testZk(): Unit = {
    log.info("start to run zookeeper cluster")
    val nodeName: String = nodeCache.head.name
    val clusterName = generateClusterName()
    result(zk_exist(clusterName)) shouldBe false
    val clientPort = CommonUtils.availablePort()
    val electionPort = CommonUtils.availablePort()
    val peerPort = CommonUtils.availablePort()
    def assert(zkCluster: ZookeeperClusterInfo): ZookeeperClusterInfo = {
      zkCluster.name shouldBe clusterName
      zkCluster.nodeNames.head shouldBe nodeName
      zkCluster.clientPort shouldBe clientPort
      zkCluster.peerPort shouldBe peerPort
      zkCluster.electionPort shouldBe electionPort
      zkCluster
    }

    val zkCluster = assert(
      result(
        zk_create(
          clusterName = clusterName,
          clientPort = clientPort,
          electionPort = electionPort,
          peerPort = peerPort,
          nodeNames = Set(nodeName)
        )))
    result(zk_start(zkCluster.name))
    try {
      assertCluster(() => result(zk_clusters()), zkCluster.name)
      assert(result(zk_cluster(zkCluster.name)))
      log.info("start to run zookeeper cluster ... done")
      result(zk_exist(zkCluster.name)) shouldBe true
      // we can't assume the size since other tests may create zk cluster at the same time
      result(zk_clusters()).isEmpty shouldBe false
      log.info(s"verify number of zk clusters... done")
      result(zk_logs(clusterName)).size shouldBe 1
      log.info(s"verify number of log... done")
      result(zk_logs(clusterName)).foreach(log =>
        withClue(log) {
          log.contains("exception") shouldBe false
          log.isEmpty shouldBe false
      })
      log.info(s"verify log of zk clusters... done")
      // since we only get "active" containers, all containers belong to the cluster should be running.
      // Currently, both k8s and pure docker have the same context of "RUNNING".
      // It is ok to filter container via RUNNING state.
      await(() => {
        val containers = result(zk_containers(clusterName))
        containers.nonEmpty && containers.map(_.state).forall(_.equals(ContainerState.RUNNING.name))
      })
      val container = result(zk_containers(clusterName)).head
      log.info(s"get containers from zk:$clusterName... done")
      container.nodeName shouldBe nodeName
      container.name.contains(clusterName) shouldBe true
      container.hostname.contains(nodeName) shouldBe true
      container.portMappings.head.portPairs.size shouldBe 3
      container.portMappings.head.portPairs.exists(_.containerPort == clientPort) shouldBe true
      container.portMappings.head.portPairs.exists(_.containerPort == electionPort) shouldBe true
      container.portMappings.head.portPairs.exists(_.containerPort == peerPort) shouldBe true
      container.environments.exists(_._2 == clientPort.toString) shouldBe true
      container.environments.exists(_._2 == electionPort.toString) shouldBe true
      container.environments.exists(_._2 == peerPort.toString) shouldBe true
    } finally if (cleanup) result(cleanZookeeper(clusterName))
  }

  @Test
  def testBroker(): Unit = {
    val zkCluster = result(
      zk_create(
        clusterName = generateClusterName(),
        clientPort = CommonUtils.availablePort(),
        electionPort = CommonUtils.availablePort(),
        peerPort = CommonUtils.availablePort(),
        nodeNames = Set(nodeCache.head.name)
      ))
    result(zk_start(zkCluster.name))
    assertCluster(() => result(zk_clusters()), zkCluster.name)
    // since we only get "active" containers, all containers belong to the cluster should be running.
    // Currently, both k8s and pure docker have the same context of "RUNNING".
    // It is ok to filter container via RUNNING state.
    await(() => {
      val containers = result(zk_containers(zkCluster.name))
      containers.nonEmpty && containers.map(_.state).forall(_.equals(ContainerState.RUNNING.name))
    })
    try {
      log.info("[BROKER] start to run broker cluster")
      val clusterName = generateClusterName()
      result(bk_exist(clusterName)) shouldBe false
      log.info(s"[BROKER] verify existence of broker cluster:$clusterName...done")
      val nodeName: String = nodeCache.head.name
      val clientPort = CommonUtils.availablePort()
      val exporterPort = CommonUtils.availablePort()
      val jmxPort = CommonUtils.availablePort()
      def assert(brokerCluster: BrokerClusterInfo): BrokerClusterInfo = {
        brokerCluster.zookeeperClusterName shouldBe zkCluster.name
        brokerCluster.name shouldBe clusterName
        brokerCluster.nodeNames.head shouldBe nodeName
        brokerCluster.clientPort shouldBe clientPort
        brokerCluster.exporterPort shouldBe exporterPort
        brokerCluster.jmxPort shouldBe jmxPort
        brokerCluster
      }

      val bkCluster = assert(
        result(
          bk_create(
            clusterName = clusterName,
            clientPort = clientPort,
            exporterPort = exporterPort,
            jmxPort = jmxPort,
            zkClusterName = zkCluster.name,
            nodeNames = Set(nodeName)
          )))
      result(bk_start(bkCluster.name))
      log.info("[BROKER] start to run broker cluster...done")
      assertCluster(() => result(bk_clusters()), bkCluster.name)
      assert(result(bk_cluster(bkCluster.name)))
      log.info("[BROKER] verify cluster api...done")
      try {
        result(bk_exist(bkCluster.name)) shouldBe true
        // we can't assume the size since other tests may create zk cluster at the same time
        result(bk_clusters()).isEmpty shouldBe false
        // since we only get "active" containers, all containers belong to the cluster should be running.
        // Currently, both k8s and pure docker have the same context of "RUNNING".
        // It is ok to filter container via RUNNING state.
        await(() => {
          val containers = result(bk_containers(clusterName))
          containers.nonEmpty && containers.map(_.state).forall(_.equals(ContainerState.RUNNING.name))
        })
        result(bk_containers(clusterName)).foreach { container =>
          container.nodeName shouldBe nodeName
          container.name.contains(clusterName) shouldBe true
          container.hostname.contains(clusterName) shouldBe true
          container.portMappings.head.portPairs.size shouldBe 3
          container.portMappings.head.portPairs.exists(_.containerPort == clientPort) shouldBe true
          container.environments.exists(_._2 == clientPort.toString) shouldBe true
        }
        result(bk_logs(clusterName)).size shouldBe 1
        result(bk_logs(clusterName)).foreach(log =>
          withClue(log) {
            log.contains("exception") shouldBe false
            log.isEmpty shouldBe false
        })
        log.info("[BROKER] verify:log done")
        var curCluster = bkCluster
        testTopic(curCluster)
        testJmx(curCluster)
        curCluster = testAddNodeToRunningBrokerCluster(curCluster)
        testTopic(curCluster)
        testJmx(curCluster)
        curCluster = testRemoveNodeToRunningBrokerCluster(curCluster)
        testTopic(curCluster)
        testJmx(curCluster)
      } finally if (cleanup) {
        result(cleanBroker(bkCluster.name))
        assertNoCluster(() => result(bk_clusters()), bkCluster.name)
      }
    } finally if (cleanup) result(cleanZookeeper(zkCluster.name))
  }

  private[this] def testAddNodeToRunningBrokerCluster(previousCluster: BrokerClusterInfo): BrokerClusterInfo = {
    await(() => result(bk_exist(previousCluster.name)))
    log.info(s"[BROKER] nodeCache:$nodeCache previous:${previousCluster.nodeNames}")
    an[IllegalArgumentException] should be thrownBy result(
      bk_removeNode(previousCluster.name, previousCluster.nodeNames.head))
    val freeNodes = nodeCache.filterNot(node => previousCluster.nodeNames.contains(node.name))
    if (freeNodes.nonEmpty) {
      // nothing happens if we add duplicate nodes
      result(bk_addNode(previousCluster.name, previousCluster.nodeNames.head))
      // we can't add a nonexistent node
      // we always get IllegalArgumentException if we sent request by restful api
      // However, if we use collie impl, an NoSuchElementException will be thrown...
      an[Throwable] should be thrownBy result(bk_addNode(previousCluster.name, CommonUtils.randomString()))
      val newNode = freeNodes.head.name
      log.info(s"[BROKER] add new node:$newNode to cluster:${previousCluster.name}")
      val newCluster = result(bk_addNode(previousCluster.name, newNode))
      log.info(s"[BROKER] add new node:$newNode to cluster:${previousCluster.name}...done")
      newCluster.name shouldBe previousCluster.name
      newCluster.imageName shouldBe previousCluster.imageName
      newCluster.zookeeperClusterName shouldBe previousCluster.zookeeperClusterName
      newCluster.exporterPort shouldBe previousCluster.exporterPort
      newCluster.clientPort shouldBe previousCluster.clientPort
      newCluster.nodeNames.size - previousCluster.nodeNames.size shouldBe 1
      await(() => result(bk_cluster(newCluster.name)).nodeNames.contains(newNode))
      newCluster
    } else previousCluster
  }

  private[this] def testTopic(cluster: BrokerClusterInfo): Unit = {
    val topicName = CommonUtils.randomString()
    val brokers = cluster.nodeNames.map(_ + s":${cluster.clientPort}").mkString(",")
    log.info(s"[BROKER] start to create topic:$topicName on broker cluster:$brokers")
    val brokerClient = BrokerClient.of(brokers)
    try {
      log.info(s"[BROKER] start to check the sync information. active broker nodes:${cluster.nodeNames}")
      // make sure all active broker nodes are sync!
      await(
        () => {
          brokerClient.brokerPorts().size() == cluster.nodeNames.size
        }
      )
      log.info(s"[BROKER] start to check the sync information. active broker nodes:${cluster.nodeNames} ... done")
      log.info(s"[BROKER] number of replications:${cluster.nodeNames.size}")
      await(
        () => {
          try {
            brokerClient.topicCreator().numberOfPartitions(1).topicName(topicName).create()
            true
          } catch {
            case e: OharaExecutionException =>
              e.getCause match {
                // the new broker needs time to sync information to other existed bk nodes.
                case _: InvalidReplicationFactorException => false
                case _: Throwable                         => throw e.getCause
              }
            case e: OharaTimeoutException =>
              log.error(s"[BROKER] create topic error ${e.getCause}")
              false
          }
        }
      )
      log.info(s"[BROKER] start to create topic:$topicName on broker cluster:$brokers ... done")
      val producer = Producer
        .builder[String, String]()
        .connectionProps(brokers)
        .allAcks()
        .keySerializer(Serializer.STRING)
        .valueSerializer(Serializer.STRING)
        .build()
      log.info(s"[BROKER] start to send data")
      try {
        await(
          () => {
            try producer
              .sender()
              .key("abc")
              .value("abc_value")
              .topicName(topicName)
              .send()
              .get()
              .topicName() == topicName
            catch {
              case e: ExecutionException =>
                e.getCause match {
                  case _: UnknownTopicOrPartitionException => false
                }
            }
          }
        )

      } finally producer.close()
      log.info(s"[BROKER] start to send data ... done")
      log.info(s"[BROKER] start to receive data")
      val consumer = Consumer
        .builder[String, String]()
        .connectionProps(brokers)
        .offsetFromBegin()
        .topicName(topicName)
        .keySerializer(Serializer.STRING)
        .valueSerializer(Serializer.STRING)
        .build()
      try {
        val records = consumer.poll(Duration.ofSeconds(30), 1)
        records.size() shouldBe 1
        records.get(0).key().get shouldBe "abc"
        records.get(0).value().get shouldBe "abc_value"
        log.info(s"[BROKER] start to receive data ... done")
      } finally consumer.close()
      brokerClient.deleteTopic(topicName)
    } finally brokerClient.close()

  }

  private[this] def testRemoveNodeToRunningBrokerCluster(previousCluster: BrokerClusterInfo): BrokerClusterInfo = {
    await(() => result(bk_exist(previousCluster.name)))
    if (previousCluster.nodeNames.size > 1) {
      val beRemovedNode = previousCluster.nodeNames.head
      await(() => {
        log.info(s"[BROKER] start to remove node:$beRemovedNode from bk cluster:${previousCluster.name}")
        result(bk_removeNode(previousCluster.name, beRemovedNode))
        log.info(s"[BROKER] start to remove node:$beRemovedNode from bk cluster:${previousCluster.name} ... done")
        val newCluster = result(bk_cluster(previousCluster.name))
        newCluster.name == previousCluster.name &&
        newCluster.imageName == previousCluster.imageName &&
        newCluster.zookeeperClusterName == previousCluster.zookeeperClusterName &&
        newCluster.clientPort == previousCluster.clientPort &&
        previousCluster.nodeNames.size - newCluster.nodeNames.size == 1 &&
        !result(bk_cluster(newCluster.name)).nodeNames.contains(beRemovedNode)
      })
      result(bk_cluster(previousCluster.name))
    } else previousCluster
  }

  @Test
  def testWorker(): Unit = {
    val zkCluster = result(
      zk_create(
        clusterName = generateClusterName(),
        clientPort = CommonUtils.availablePort(),
        electionPort = CommonUtils.availablePort(),
        peerPort = CommonUtils.availablePort(),
        nodeNames = Set(nodeCache.head.name)
      ))
    result(zk_start(zkCluster.name))
    try {
      assertCluster(() => result(zk_clusters()), zkCluster.name)
      // since we only get "active" containers, all containers belong to the cluster should be running.
      // Currently, both k8s and pure docker have the same context of "RUNNING".
      // It is ok to filter container via RUNNING state.
      await(() => {
        val containers = result(zk_containers(zkCluster.name))
        containers.nonEmpty && containers.map(_.state).forall(_.equals(ContainerState.RUNNING.name))
      })
      val bkCluster = result(
        bk_create(
          clusterName = generateClusterName(),
          clientPort = CommonUtils.availablePort(),
          exporterPort = CommonUtils.availablePort(),
          jmxPort = CommonUtils.availablePort(),
          zkClusterName = zkCluster.name,
          nodeNames = Set(nodeCache.head.name)
        ))
      result(bk_start(bkCluster.name))
      try {
        assertCluster(() => result(bk_clusters()), bkCluster.name)
        // since we only get "active" containers, all containers belong to the cluster should be running.
        // Currently, both k8s and pure docker have the same context of "RUNNING".
        // It is ok to filter container via RUNNING state.
        await(() => {
          val containers = result(bk_containers(bkCluster.name))
          containers.nonEmpty && containers.map(_.state).forall(_.equals(ContainerState.RUNNING.name))
        })
        log.info("[WORKER] start to test worker")
        val nodeName = nodeCache.head.name
        val clusterName = generateClusterName()
        result(wk_exist(clusterName)) shouldBe false
        log.info("[WORKER] verify:nonExists done")
        val clientPort = CommonUtils.availablePort()
        val jmxPort = CommonUtils.availablePort()
        def assert(workerCluster: WorkerClusterInfo): WorkerClusterInfo = {
          workerCluster.brokerClusterName shouldBe bkCluster.name
          workerCluster.name shouldBe clusterName
          workerCluster.nodeNames.head shouldBe nodeName
          workerCluster.clientPort shouldBe clientPort
          workerCluster.jmxPort shouldBe jmxPort
          workerCluster.configTopicPartitions shouldBe 1
          workerCluster.configTopicReplications shouldBe 1
          workerCluster.statusTopicPartitions shouldBe 1
          workerCluster.statusTopicReplications shouldBe 1
          workerCluster.offsetTopicPartitions shouldBe 1
          workerCluster.offsetTopicReplications shouldBe 1
          workerCluster
        }
        log.info("[WORKER] create ...")
        val wkCluster = assert(
          result(
            wk_create(
              clusterName = clusterName,
              clientPort = clientPort,
              jmxPort = jmxPort,
              bkClusterName = bkCluster.name,
              nodeNames = Set(nodeName)
            )))
        log.info("[WORKER] create done")
        result(wk_start(wkCluster.name))
        log.info("[WORKER] start done")
        assertCluster(() => result(wk_clusters()), wkCluster.name)
        log.info("[WORKER] check existence")
        assert(result(wk_cluster(wkCluster.name)))
        log.info("[WORKER] verify:create done")
        try {
          result(wk_exist(wkCluster.name)) shouldBe true
          log.info("[WORKER] verify:exist done")
          // we can't assume the size since other tests may create zk cluster at the same time
          result(wk_clusters()).isEmpty shouldBe false
          log.info("[WORKER] verify:list done")
          // since we only get "active" containers, all containers belong to the cluster should be running.
          // Currently, both k8s and pure docker have the same context of "RUNNING".
          // It is ok to filter container via RUNNING state.
          await(() => {
            val containers = result(wk_containers(clusterName))
            containers.nonEmpty && containers.map(_.state).forall(_.equals(ContainerState.RUNNING.name))
          })
          result(wk_containers(clusterName)).foreach { container =>
            container.nodeName shouldBe nodeName
            container.name.contains(clusterName) shouldBe true
            container.hostname.contains(clusterName) shouldBe true
            // [BEFORE] ClusterCollieImpl applies --network=host to all worker containers so there is no port mapping.
            // The following checks are disabled rather than deleted since it seems like a bug if we don't check the port mapping.
            // [AFTER] ClusterCollieImpl use bridge network now
            container.portMappings.head.portPairs.size shouldBe 2
            container.portMappings.head.portPairs.exists(_.containerPort == clientPort) shouldBe true
            container.environments.exists(_._2 == clientPort.toString) shouldBe true
          }
          val logs = result(wk_logs(clusterName))
          logs.size shouldBe 1
          logs.foreach(log =>
            withClue(log) {
              log.contains("- ERROR") shouldBe false
              // we cannot assume "k8s get logs" are complete since log may rotate
              // so log could be empty in k8s environment
              // also see : https://github.com/kubernetes/kubernetes/issues/11046#issuecomment-121140315
          })
          log.info("[WORKER] verify:log done")
          var curCluster = wkCluster
          testConnectors(curCluster)
          testJmx(curCluster)
          curCluster = testAddNodeToRunningWorkerCluster(curCluster)
          testConnectors(curCluster)
          testJmx(curCluster)
          curCluster = testRemoveNodeToRunningWorkerCluster(curCluster)
          testConnectors(curCluster)
          testJmx(curCluster)
        } finally if (cleanup) {
          result(cleanWorker(wkCluster.name))
          assertNoCluster(() => result(wk_clusters()), wkCluster.name)
        }
      } finally if (cleanup) {
        result(cleanBroker(bkCluster.name))
        assertNoCluster(() => result(bk_clusters()), bkCluster.name)
      }
    } finally if (cleanup) result(cleanZookeeper(zkCluster.name))

  }

  private[this] def testConnectors(cluster: WorkerClusterInfo): Unit =
    await(
      () =>
        try {
          log.info(s"worker node head: ${cluster.nodeNames.head}:${cluster.clientPort}")
          result(WorkerClient(s"${cluster.nodeNames.head}:${cluster.clientPort}").connectorDefinitions()).nonEmpty
        } catch {
          case e: Throwable =>
            log.info(s"[WORKER] worker cluster:${cluster.name} is starting ... retry", e)
            false
      }
    )

  private[this] def testJmx(cluster: BrokerClusterInfo): Unit = cluster.nodeNames.foreach { node =>
    BeanChannel.builder().hostname(node).port(cluster.jmxPort).build().size() should not be 0
  }

  private[this] def testJmx(cluster: WorkerClusterInfo): Unit = cluster.nodeNames.foreach { node =>
    BeanChannel.builder().hostname(node).port(cluster.jmxPort).build().size() should not be 0
  }

  private[this] def testAddNodeToRunningWorkerCluster(previousCluster: WorkerClusterInfo): WorkerClusterInfo = {
    await(() => result(wk_exist(previousCluster.name)))
    an[IllegalArgumentException] should be thrownBy result(
      wk_removeNode(previousCluster.name, previousCluster.nodeNames.head))
    val freeNodes = nodeCache.filterNot(node => previousCluster.nodeNames.contains(node.name))
    if (freeNodes.nonEmpty) {
      val newNode = freeNodes.head.name
      // it is ok to add duplicate nodes
      result(wk_addNode(previousCluster.name, previousCluster.nodeNames.head))
      // we can't add a nonexistent node
      // we always get IllegalArgumentException if we sent request by restful api
      // However, if we use collie impl, an NoSuchElementException will be thrown...
      an[Throwable] should be thrownBy result(wk_addNode(previousCluster.name, CommonUtils.randomString()))
      log.info(s"[WORKER] start to add node:$newNode to a running worker cluster")
      val newCluster = result(wk_addNode(previousCluster.name, newNode))
      newCluster.name shouldBe previousCluster.name
      newCluster.imageName shouldBe previousCluster.imageName
      newCluster.configTopicName shouldBe previousCluster.configTopicName
      newCluster.statusTopicName shouldBe previousCluster.statusTopicName
      newCluster.offsetTopicName shouldBe previousCluster.offsetTopicName
      newCluster.groupId shouldBe previousCluster.groupId
      newCluster.brokerClusterName shouldBe previousCluster.brokerClusterName
      newCluster.clientPort shouldBe previousCluster.clientPort
      newCluster.nodeNames.size - previousCluster.nodeNames.size shouldBe 1
      await(() => result(wk_cluster(newCluster.name)).nodeNames.contains(newNode))
      newCluster
    } else previousCluster
  }

  private[this] def testRemoveNodeToRunningWorkerCluster(previousCluster: WorkerClusterInfo): WorkerClusterInfo = {
    await(() => result(wk_exist(previousCluster.name)))
    if (previousCluster.nodeNames.size > 1) {
      val beRemovedNode = previousCluster.nodeNames.head
      await(() => {
        log.info(s"[WORKER] start to remove node:$beRemovedNode from ${previousCluster.name}")
        result(wk_removeNode(previousCluster.name, beRemovedNode))
        log.info(s"[WORKER] start to remove node:$beRemovedNode from ${previousCluster.name} ... done")
        val newCluster = result(wk_cluster(previousCluster.name))
        newCluster.name == previousCluster.name &&
        newCluster.imageName == previousCluster.imageName &&
        newCluster.configTopicName == previousCluster.configTopicName &&
        newCluster.statusTopicName == previousCluster.statusTopicName &&
        newCluster.offsetTopicName == previousCluster.offsetTopicName &&
        newCluster.groupId == previousCluster.groupId &&
        newCluster.brokerClusterName == previousCluster.brokerClusterName &&
        newCluster.clientPort == previousCluster.clientPort &&
        previousCluster.nodeNames.size - newCluster.nodeNames.size == 1 &&
        !result(wk_cluster(newCluster.name)).nodeNames.contains(beRemovedNode)
      })
      result(wk_cluster(previousCluster.name))
    } else previousCluster
  }

  @Test
  def testMultiZkClustersOnSingleNode(): Unit = {
    val names = (0 until numberOfClusters).map(_ => generateClusterName())
    try {
      val clusters = names.map { name =>
        result(
          zk_create(
            clusterName = name,
            clientPort = CommonUtils.availablePort(),
            electionPort = CommonUtils.availablePort(),
            peerPort = CommonUtils.availablePort(),
            nodeNames = nodeCache.map(_.name).toSet
          ).flatMap(info => zk_start(info.name).flatMap(_ => zk_cluster(info.name))))
      }
      // add a bit wait to make sure the cluster is up
      TimeUnit.SECONDS.sleep(5)
      assertClusters(() => result(zk_clusters()), clusters.map(_.name))
      val clusters2 = result(zk_clusters())
      clusters.foreach { c =>
        val another = clusters2.find(_.name == c.name).get
        another.name shouldBe c.name
        another.peerPort shouldBe c.peerPort
        another.clientPort shouldBe c.clientPort
        another.imageName shouldBe c.imageName
        another.electionPort shouldBe c.electionPort
        another.nodeNames shouldBe c.nodeNames
        result(zk_logs(c.name)).foreach { log =>
          // If we start a single-node zk cluster, zk print a "error" warning to us to say that you are using a single-node,
          // and we won't see the connection exception since there is only a node.
          if (nodeCache.size == 1) withClue(log)(log.toLowerCase.contains("exception") shouldBe false)
          // By contrast, if we start a true zk cluster, the exception ensues since the connections between nodes fail in beginning.
          else withClue(log)(log.toLowerCase.contains("- ERROR") shouldBe false)
          log.isEmpty shouldBe false
        }
      }
    } finally if (cleanup) names.foreach { name =>
      try result(cleanZookeeper(name))
      catch {
        case _: Throwable =>
        // do nothing
      }
    }
  }

  @Test
  def testMultiBkClustersOnSingleNode(): Unit = {
    val zkNames = (0 until numberOfClusters).map(_ => generateClusterName())
    val bkNames = (0 until numberOfClusters).map(_ => generateClusterName())
    try {
      // NOTED: It is illegal to run multi bk clusters on same zk cluster so we have got to instantiate multi zk clusters first.
      val zks = zkNames.map { name =>
        result(
          zk_create(
            clusterName = name,
            clientPort = CommonUtils.availablePort(),
            electionPort = CommonUtils.availablePort(),
            peerPort = CommonUtils.availablePort(),
            nodeNames = Set(nodeCache.head.name)
          ).flatMap(info => zk_start(info.name).flatMap(_ => zk_cluster(info.name))))
      }

      assertClusters(() => result(zk_clusters()), zks.map(_.name))
      // since we only get "active" containers, all containers belong to the cluster should be running.
      // Currently, both k8s and pure docker have the same context of "RUNNING".
      // It is ok to filter container via RUNNING state.
      await(() =>
        zkNames.forall(name => {
          val containers = result(zk_containers(name))
          containers.nonEmpty && containers.map(_.state).forall(_.equals(ContainerState.RUNNING.name))
        }))
      zks.zipWithIndex.foreach {
        case (zk, index) =>
          val bkCluster = result(
            bk_create(
              clusterName = bkNames(index),
              clientPort = CommonUtils.availablePort(),
              exporterPort = CommonUtils.availablePort(),
              jmxPort = CommonUtils.availablePort(),
              zkClusterName = zk.name,
              nodeNames = Set(nodeCache.head.name)
            ))
          result(bk_start(bkCluster.name))
          testTopic(bkCluster)
      }
      //TODO #1358 Integration test timeout exception for check topic on TestK8SClusterCollie class
      /*assertClusters(() => result(bk_clusters()), bks.map(_.name))
      result(bk_clusters()).foreach { broker =>
        testTopic(broker)
      }*/
    } finally if (cleanup) {
      bkNames.foreach { name =>
        log.info(s"[Broker] Remove broker name is $name")
        try result(cleanBroker(name))
        catch {
          case _: Throwable =>
          // do nothing
        }
      }
      assertNoClusters(() => result(bk_clusters()), bkNames)
      zkNames.foreach { name =>
        try result(cleanZookeeper(name))
        catch {
          case _: Throwable =>
          // do nothing
        }
      }
    }
  }

  @Test
  def testMultiWkClustersOnSingleNode(): Unit = {
    val zkName = generateClusterName()
    val bkName = generateClusterName()
    val wkNames = (0 until numberOfClusters).map(_ => generateClusterName())
    val groupIds = (0 until numberOfClusters).map(_ => CommonUtils.randomString(10))
    val configTopicNames = (0 until numberOfClusters).map(_ => CommonUtils.randomString(10))
    val offsetTopicNames = (0 until numberOfClusters).map(_ => CommonUtils.randomString(10))
    val statusTopicNames = (0 until numberOfClusters).map(_ => CommonUtils.randomString(10))
    try {
      log.info(s"start to run zk cluster:$zkName")
      val zk = result(
        zk_create(
          clusterName = zkName,
          clientPort = CommonUtils.availablePort(),
          electionPort = CommonUtils.availablePort(),
          peerPort = CommonUtils.availablePort(),
          nodeNames = Set(nodeCache.head.name)
        )
      )
      result(zk_start(zk.name))
      assertCluster(() => result(zk_clusters()), zk.name)
      // since we only get "active" containers, all containers belong to the cluster should be running.
      // Currently, both k8s and pure docker have the same context of "RUNNING".
      // It is ok to filter container via RUNNING state.
      await(() => {
        val containers = result(zk_containers(zkName))
        containers.nonEmpty && containers.map(_.state).forall(_.equals(ContainerState.RUNNING.name))
      })

      log.info(s"start to run bk cluster:$bkName")
      val bk = result(
        bk_create(
          clusterName = bkName,
          clientPort = CommonUtils.availablePort(),
          exporterPort = CommonUtils.availablePort(),
          jmxPort = CommonUtils.availablePort(),
          zkClusterName = zk.name,
          nodeNames = Set(nodeCache.head.name)
        ))
      result(bk_start(bk.name))
      assertCluster(() => result(bk_clusters()), bk.name)
      // since we only get "active" containers, all containers belong to the cluster should be running.
      // Currently, both k8s and pure docker have the same context of "RUNNING".
      // It is ok to filter container via RUNNING state.
      await(() => {
        val containers = result(bk_containers(bkName))
        containers.nonEmpty && containers.map(_.state).forall(_.equals(ContainerState.RUNNING.name))
      })

      log.info(s"start to run multi wk clusters:$wkNames")
      val clusters = wkNames.zipWithIndex.map {
        case (wkName, index) =>
          result(
            wk_create(
              clusterName = wkName,
              clientPort = CommonUtils.availablePort(),
              jmxPort = CommonUtils.availablePort(),
              bkClusterName = bk.name,
              groupId = groupIds(index),
              configTopicName = configTopicNames(index),
              offsetTopicName = offsetTopicNames(index),
              statusTopicName = statusTopicNames(index),
              nodeNames = nodeCache.map(_.name).toSet
            ))
      }
      clusters.foreach(wk => result(wk_start(wk.name)))
      log.info(s"check multi wk clusters:$wkNames")
      // add a bit wait to make sure the cluster is up
      TimeUnit.SECONDS.sleep(10)
      assertClusters(() => result(wk_clusters()), clusters.map(_.name))
      wkNames.zipWithIndex.map {
        case (wkName, index) =>
          clusters.find(_.name == wkName).get.groupId shouldBe groupIds(index)
          clusters.find(_.name == wkName).get.configTopicName shouldBe configTopicNames(index)
          clusters.find(_.name == wkName).get.offsetTopicName shouldBe offsetTopicNames(index)
          clusters.find(_.name == wkName).get.statusTopicName shouldBe statusTopicNames(index)
          clusters.find(_.name == wkName).get.brokerClusterName shouldBe bk.name
      }

      log.info(s"check multi wk clusters:$wkNames by list")
      result(wk_clusters()).foreach { cluster =>
        testConnectors(cluster)
        testJmx(cluster)
      }
    } finally if (cleanup) {
      wkNames.foreach { name =>
        try result(cleanWorker(name))
        catch {
          case _: Throwable =>
          // do nothing
        }
      }
      assertNoClusters(() => result(wk_clusters()), wkNames)
      try result(cleanBroker(bkName))
      catch {
        case _: Throwable =>
        // do nothing
      }
      assertNoCluster(() => result(bk_clusters()), bkName)
      try result(cleanZookeeper(zkName))
      catch {
        case _: Throwable =>
        // do nothing
      }
    }
  }
}
