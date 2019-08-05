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

package com.island.ohara.configurator.route

import com.island.ohara.client.configurator.v0.{BrokerApi, NodeApi, WorkerApi, ZookeeperApi}
import com.island.ohara.common.rule.MediumTest
import com.island.ohara.common.util.{CommonUtils, Releasable}
import com.island.ohara.configurator.Configurator
import com.island.ohara.configurator.fake.{FakeWorkerClient, FakeWorkerCollie}
import org.junit.{After, Before, Test}
import org.scalatest.Matchers

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
class TestWorkerRoute extends MediumTest with Matchers {
  private[this] val numberOfCluster = 1
  private[this] val configurator = Configurator.builder.fake(numberOfCluster, 0).build()

  /**
    * a fake cluster has 3 fake node.
    */
  private[this] val numberOfDefaultNodes = 3 * numberOfCluster
  private[this] val workerApi = WorkerApi.access.hostname(configurator.hostname).port(configurator.port)

  private[this] val bkClusterName =
    Await.result(BrokerApi.access.hostname(configurator.hostname).port(configurator.port).list(), 10 seconds).head.name

  private[this] val nodeNames: Set[String] = Set("n0", "n1")

  @Before
  def setup(): Unit = {
    val nodeAccess = NodeApi.access.hostname(configurator.hostname).port(configurator.port)

    nodeNames.isEmpty shouldBe false

    nodeNames.foreach { n =>
      result(nodeAccess.request.hostname(n).port(22).user("user").password("pwd").create())
    }

    result(nodeAccess.list()).size shouldBe (nodeNames.size + numberOfDefaultNodes)
  }

  @Test
  def repeatedlyDelete(): Unit = {
    (0 to 10).foreach { index =>
      result(workerApi.delete(index.toString))
      result(workerApi.removeNode(index.toString, index.toString))
    }
  }

  @Test
  def testDefaultBk(): Unit =
    result(workerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).create()).brokerClusterName shouldBe bkClusterName

  @Test
  def runOnIncorrectBk(): Unit =
    an[IllegalArgumentException] should be thrownBy result(
      workerApi.request
        .name(CommonUtils.randomString(10))
        .nodeNames(nodeNames)
        .brokerClusterName(CommonUtils.randomString())
        .create()
        .flatMap(wk => workerApi.start(wk.name)))

  @Test
  def testAllSetting(): Unit = {
    val name = CommonUtils.randomString(10)
    val clientPort = CommonUtils.availablePort()
    val jmxPort = CommonUtils.availablePort()
    val groupId = CommonUtils.randomString(10)
    val configTopicName = CommonUtils.randomString(10)
    val configTopicReplications: Short = 2
    val offsetTopicName = CommonUtils.randomString(10)
    val offsetTopicPartitions = 2
    val offsetTopicReplications: Short = 2
    val statusTopicName = CommonUtils.randomString(10)
    val statusTopicPartitions = 2
    val statusTopicReplications: Short = 2

    val wkCluster = result(
      workerApi.request
        .name(name)
        .clientPort(clientPort)
        .jmxPort(jmxPort)
        .groupId(groupId)
        .configTopicName(configTopicName)
        .configTopicReplications(configTopicReplications)
        .offsetTopicName(offsetTopicName)
        .offsetTopicPartitions(offsetTopicPartitions)
        .offsetTopicReplications(offsetTopicReplications)
        .statusTopicName(statusTopicName)
        .statusTopicPartitions(statusTopicPartitions)
        .statusTopicReplications(statusTopicReplications)
        .nodeNames(nodeNames)
        .create())
    wkCluster.jmxPort shouldBe jmxPort
    wkCluster.clientPort shouldBe clientPort
    wkCluster.groupId shouldBe groupId
    wkCluster.configTopicName shouldBe configTopicName
    wkCluster.configTopicReplications shouldBe configTopicReplications
    wkCluster.offsetTopicName shouldBe offsetTopicName
    wkCluster.offsetTopicPartitions shouldBe offsetTopicPartitions
    wkCluster.offsetTopicReplications shouldBe offsetTopicReplications
    wkCluster.statusTopicName shouldBe statusTopicName
    wkCluster.statusTopicPartitions shouldBe statusTopicPartitions
    wkCluster.statusTopicReplications shouldBe statusTopicReplications
  }

  @Test
  def testDefaultBrokerInMultiBrokerCluster(): Unit = {
    val zkClusterName = CommonUtils.randomString(10)
    val zk = result(
      ZookeeperApi.access
        .hostname(configurator.hostname)
        .port(configurator.port)
        .request
        .name(zkClusterName)
        .nodeNames(nodeNames)
        .create())
    zk.name shouldBe zkClusterName
    result(ZookeeperApi.access.hostname(configurator.hostname).port(configurator.port).start(zk.name))
    val anotherBk = result(
      BrokerApi.access
        .hostname(configurator.hostname)
        .port(configurator.port)
        .request
        .name(CommonUtils.randomString(10))
        .zookeeperClusterName(zkClusterName)
        .nodeNames(nodeNames)
        .create())
    result(BrokerApi.access.hostname(configurator.hostname).port(configurator.port).start(anotherBk.name))
    result(BrokerApi.access.hostname(configurator.hostname).port(configurator.port).list()).size shouldBe 2

    // there are two bk cluster so we have to assign the bk cluster...
    an[IllegalArgumentException] should be thrownBy result(
      workerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).create()
    )
    // pass
    result(
      workerApi.request
        .name(CommonUtils.randomString(10))
        .nodeNames(nodeNames)
        .brokerClusterName(anotherBk.name)
        .create()
    )
  }

  @Test
  def testCreateOnNonexistentNode(): Unit =
    an[IllegalArgumentException] should be thrownBy result(
      workerApi.request
        .name(CommonUtils.randomString(10))
        .nodeName(CommonUtils.randomString())
        .create()
        .flatMap(wk => workerApi.start(wk.name))
    )

  @Test
  def testImageName(): Unit = {
    result(
      workerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).create()
    ).imageName shouldBe WorkerApi.IMAGE_NAME_DEFAULT

    //  the available images in fake mode is only IMAGE_NAME_DEFAULT
    an[IllegalArgumentException] should be thrownBy result(
      workerApi.request
        .name(CommonUtils.randomString(10))
        .nodeNames(nodeNames)
        .imageName(CommonUtils.randomString(10))
        .create()
        .flatMap(wk => workerApi.start(wk.name))
    )
  }
  @Test
  def testList(): Unit = {
    val count = 5
    (0 until count).foreach { _ =>
      result(
        workerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).create()
      )
    }
    result(workerApi.list()).size shouldBe count
  }

  @Test
  def testRemove(): Unit = {
    val cluster = result(
      workerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).create()
    )
    result(workerApi.list()).size shouldBe 1
    result(workerApi.delete(cluster.name))
    result(workerApi.list()).size shouldBe 0
  }

  @Test
  def testAddNode(): Unit = {
    val cluster = result(
      workerApi.request.name(CommonUtils.randomString(10)).nodeName(nodeNames.head).create()
    )
    result(workerApi.start(cluster.name))
    result(workerApi.addNode(cluster.name, nodeNames.last).flatMap(_ => workerApi.get(cluster.name))).nodeNames shouldBe
      cluster.copy(nodeNames = cluster.nodeNames ++ Set(nodeNames.last)).nodeNames
  }

  @Test
  def testRemoveNode(): Unit = {
    val cluster = result(
      workerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).create()
    )
    result(workerApi.start(cluster.name))
    cluster.nodeNames shouldBe nodeNames
    result(workerApi.removeNode(cluster.name, nodeNames.last))
    result(workerApi.get(cluster.name)).nodeNames shouldBe nodeNames - nodeNames.last
  }

  @Test
  def testInvalidClusterName(): Unit = an[IllegalArgumentException] should be thrownBy result(
    workerApi.request.name("123123.").nodeNames(nodeNames).create()
  )

  @Test
  def createWkClusterWithSameName(): Unit = {
    val name = CommonUtils.randomString(10)

    // pass
    result(
      workerApi.request.name(name).nodeNames(nodeNames).create()
    )

    // we don't need to create another bk cluster since it is feasible to create multi wk cluster on same broker cluster
    an[IllegalArgumentException] should be thrownBy result(
      workerApi.request.name(name).nodeNames(nodeNames).create()
    )
  }

  @Test
  def clientPortConflict(): Unit = {
    val clientPort = CommonUtils.availablePort()
    // pass
    result(
      workerApi.request
        .name(CommonUtils.randomString(10))
        .clientPort(clientPort)
        .nodeNames(nodeNames)
        .create()
        .flatMap(wk => workerApi.start(wk.name))
    )

    an[IllegalArgumentException] should be thrownBy result(
      workerApi.request
        .name(CommonUtils.randomString(10))
        .clientPort(clientPort)
        .nodeNames(nodeNames)
        .create()
        .flatMap(wk => workerApi.start(wk.name))
    )
  }

  @Test
  def jmxPortConflict(): Unit = {
    val jmxPort = CommonUtils.availablePort()
    // pass
    result(
      workerApi.request
        .name(CommonUtils.randomString(10))
        .jmxPort(jmxPort)
        .nodeNames(nodeNames)
        .create()
        .flatMap(wk => workerApi.start(wk.name))
    )

    an[IllegalArgumentException] should be thrownBy result(
      workerApi.request
        .name(CommonUtils.randomString(10))
        .jmxPort(jmxPort)
        .nodeNames(nodeNames)
        .create()
        .flatMap(wk => workerApi.start(wk.name))
    )
  }

  @Test
  def duplicateGroupId(): Unit = {
    val groupId = CommonUtils.randomString(10)
    // pass
    result(
      workerApi.request
        .name(CommonUtils.randomString(10))
        .groupId(groupId)
        .nodeNames(nodeNames)
        .create()
        .flatMap(wk => workerApi.start(wk.name))
    )

    an[IllegalArgumentException] should be thrownBy result(
      workerApi.request
        .name(CommonUtils.randomString(10))
        .groupId(groupId)
        .nodeNames(nodeNames)
        .create()
        .flatMap(wk => workerApi.start(wk.name))
    )
  }

  @Test
  def duplicateConfigTopic(): Unit = {
    val configTopicName = CommonUtils.randomString(10)
    // pass
    result(
      workerApi.request
        .name(CommonUtils.randomString(10))
        .configTopicName(configTopicName)
        .nodeNames(nodeNames)
        .create()
        .flatMap(wk => workerApi.start(wk.name))
    )

    an[IllegalArgumentException] should be thrownBy result(
      workerApi.request
        .name(CommonUtils.randomString(10))
        .configTopicName(configTopicName)
        .nodeNames(nodeNames)
        .create()
        .flatMap(wk => workerApi.start(wk.name))
    )
  }

  @Test
  def duplicateOffsetTopic(): Unit = {
    val offsetTopicName = CommonUtils.randomString(10)
    // pass
    result(
      workerApi.request
        .name(CommonUtils.randomString(10))
        .offsetTopicName(offsetTopicName)
        .nodeNames(nodeNames)
        .create()
        .flatMap(wk => workerApi.start(wk.name))
    )

    an[IllegalArgumentException] should be thrownBy result(
      workerApi.request
        .name(CommonUtils.randomString(10))
        .offsetTopicName(offsetTopicName)
        .nodeNames(nodeNames)
        .create()
        .flatMap(wk => workerApi.start(wk.name))
    )
  }

  @Test
  def duplicateStatusTopic(): Unit = {
    val statusTopicName = CommonUtils.randomString(10)
    // pass
    result(
      workerApi.request
        .name(CommonUtils.randomString(10))
        .statusTopicName(statusTopicName)
        .nodeNames(nodeNames)
        .create()
        .flatMap(wk => workerApi.start(wk.name))
    )

    an[IllegalArgumentException] should be thrownBy result(
      workerApi.request
        .name(CommonUtils.randomString(10))
        .statusTopicName(statusTopicName)
        .nodeNames(nodeNames)
        .create()
        .flatMap(wk => workerApi.start(wk.name))
    )
  }

  @Test
  def testForceDelete(): Unit = {
    val initialCount = configurator.clusterCollie.workerCollie.asInstanceOf[FakeWorkerCollie].forceRemoveCount

    // graceful delete
    val wk0 = result(
      workerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).create()
    )
    result(workerApi.start(wk0.name))
    result(workerApi.stop(wk0.name))
    result(workerApi.delete(wk0.name))
    configurator.clusterCollie.workerCollie.asInstanceOf[FakeWorkerCollie].forceRemoveCount shouldBe initialCount

    // force delete
    val wk1 = result(workerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).create())
    result(workerApi.start(wk1.name))
    result(workerApi.forceStop(wk1.name))
    result(workerApi.delete(wk1.name))
    configurator.clusterCollie.workerCollie.asInstanceOf[FakeWorkerCollie].forceRemoveCount shouldBe initialCount + 1
  }

  @Test
  def testConnectorDefinitions(): Unit = {
    FakeWorkerClient.localConnectorDefinitions.size should not be 0
    result(workerApi.list()).foreach(_.connectors shouldBe FakeWorkerClient.localConnectorDefinitions)
  }

  @Test
  def testConnectorDefinitionsFromPreCreatedWorkerCluster(): Unit = {
    val configurator = Configurator.builder.fake(numberOfCluster, 1).build()
    try result(configurator.clusterCollie.workerCollie.clusters()).keys
      .foreach(_.connectors shouldBe FakeWorkerClient.localConnectorDefinitions)
    finally configurator.close()
  }

  @After
  def tearDown(): Unit = Releasable.close(configurator)
}
