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
import com.island.ohara.configurator.fake.FakeBrokerCollie
import org.junit.{After, Before, Test}
import org.scalatest.Matchers

import scala.concurrent.ExecutionContext.Implicits.global
class TestBrokerRoute extends MediumTest with Matchers {
  private[this] val configurator = Configurator.builder.fake(0, 0).build()
  private[this] val brokerApi = BrokerApi.access.hostname(configurator.hostname).port(configurator.port)

  private[this] val zkClusterName = CommonUtils.randomString(10)

  private[this] val nodeNames: Set[String] = Set("n0", "n1")

  @Before
  def setup(): Unit = {
    val nodeAccess = NodeApi.access.hostname(configurator.hostname).port(configurator.port)

    nodeNames.isEmpty shouldBe false

    nodeNames.foreach { n =>
      result(
        nodeAccess.request.hostname(n).port(22).user("user").password("password").create()
      )
    }

    result(nodeAccess.list()).size shouldBe nodeNames.size

    // create zookeeper props
    result(
      ZookeeperApi.access
        .hostname(configurator.hostname)
        .port(configurator.port)
        .request
        .name(zkClusterName)
        .nodeNames(nodeNames)
        .create()
    ).name shouldBe zkClusterName

    // start zookeeper
    result(
      ZookeeperApi.access.hostname(configurator.hostname).port(configurator.port).start(zkClusterName)
    )
  }

  @Test
  def repeatedlyDelete(): Unit = {
    (0 to 10).foreach { index =>
      result(brokerApi.delete(index.toString))
      result(brokerApi.removeNode(index.toString, index.toString))
    }
  }

  @Test
  def removeBrokerClusterUsedByWorkerCluster(): Unit = {
    val bk = result(
      brokerApi.request
        .name(CommonUtils.randomString(10))
        .zookeeperClusterName(zkClusterName)
        .nodeNames(nodeNames)
        .create())
    result(brokerApi.start(bk.name))

    val wk = result(
      WorkerApi.access
        .hostname(configurator.hostname)
        .port(configurator.port)
        .request
        .name(CommonUtils.randomString(10))
        .nodeNames(nodeNames)
        .create())
    // start worker
    result(
      WorkerApi.access.hostname(configurator.hostname).port(configurator.port).start(wk.name)
    )

    val bks = result(brokerApi.list())

    bks.isEmpty shouldBe false

    // this broker cluster is used by worker cluster
    an[IllegalArgumentException] should be thrownBy result(brokerApi.stop(bk.name))

    // remove wk cluster
    result(WorkerApi.access.hostname(configurator.hostname).port(configurator.port).stop(wk.name))
    result(WorkerApi.access.hostname(configurator.hostname).port(configurator.port).delete(wk.name))

    // pass
    result(brokerApi.stop(bk.name))
    result(brokerApi.delete(bk.name))
  }

  @Test
  def testDefaultZkInMultiZkCluster(): Unit = {
    val anotherZk = CommonUtils.randomString(10)
    result(
      ZookeeperApi.access
        .hostname(configurator.hostname)
        .port(configurator.port)
        .request
        .name(anotherZk)
        .nodeNames(nodeNames)
        .create()).name shouldBe anotherZk
    result(ZookeeperApi.access.hostname(configurator.hostname).port(configurator.port).start(anotherZk))

    try {
      result(ZookeeperApi.access.hostname(configurator.hostname).port(configurator.port).list()).size shouldBe 2

      // there are two zk cluster so we have to assign the zk cluster...
      an[IllegalArgumentException] should be thrownBy result(
        brokerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).create()
      )

      val updated = result(brokerApi.request.zookeeperClusterName(anotherZk).nodeNames(nodeNames).update())
      updated.zookeeperClusterName shouldBe anotherZk
      // after assigned, start is ok
      result(brokerApi.start(updated.name))
      result(brokerApi.stop(updated.name))
    } finally {
      result(ZookeeperApi.access.hostname(configurator.hostname).port(configurator.port).stop(anotherZk))
      result(ZookeeperApi.access.hostname(configurator.hostname).port(configurator.port).delete(anotherZk))
    }
  }

  @Test
  def testCreateOnNonexistentNode(): Unit = {
    val bk = result(
      brokerApi.request.name(CommonUtils.randomString(10)).nodeName(CommonUtils.randomString(10)).create()
    )
    an[IllegalArgumentException] should be thrownBy result(brokerApi.start(bk.name))
  }

  @Test
  def testDefaultZk(): Unit = {
    val bk = result(brokerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).create())
    // absent zookeeper name will be auto-filled in creation
    bk.zookeeperClusterName shouldBe zkClusterName
    result(brokerApi.start(bk.name))
    result(brokerApi.get(bk.name)).zookeeperClusterName shouldBe zkClusterName
  }

  @Test
  def testImageName(): Unit = {
    result(brokerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).create()).imageName shouldBe BrokerApi.IMAGE_NAME_DEFAULT

    val bk = result(
      brokerApi.request
        .name(CommonUtils.randomString(10))
        .imageName(CommonUtils.randomString(10))
        .nodeNames(nodeNames)
        .create())
    // the available images of fake mode is only BrokerApi.IMAGE_NAME_DEFAULT
    an[IllegalArgumentException] should be thrownBy result(brokerApi.start(bk.name))
  }

  @Test
  def testList(): Unit = {
    val init = result(brokerApi.list()).size
    val bk = result(brokerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).create())
    result(brokerApi.start(bk.name))

    val zk2 = result(
      ZookeeperApi.access
        .hostname(configurator.hostname)
        .port(configurator.port)
        .request
        .name(CommonUtils.randomString(10))
        .nodeNames(nodeNames)
        .create()
    )
    result(ZookeeperApi.access.hostname(configurator.hostname).port(configurator.port).start(zk2.name))

    val bk2 = result(
      brokerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).zookeeperClusterName(zk2.name).create())
    result(brokerApi.start(bk2.name))

    val clusters = result(brokerApi.list())
    clusters.size shouldBe 2 + init
    clusters.exists(_.name == bk.name) shouldBe true
    clusters.exists(_.name == bk2.name) shouldBe true
  }

  @Test
  def testStop(): Unit = {
    val init = result(brokerApi.list()).size
    val cluster = result(brokerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).create())
    result(brokerApi.start(cluster.name))
    result(brokerApi.list()).size shouldBe init + 1
    result(brokerApi.stop(cluster.name))
    result(brokerApi.delete(cluster.name))
    result(brokerApi.list()).size shouldBe init
  }

  @Test
  def testRemove(): Unit = {
    val init = result(brokerApi.list()).size
    val cluster = result(brokerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).create())
    result(brokerApi.list()).size shouldBe init + 1
    result(brokerApi.delete(cluster.name))
    result(brokerApi.list()).size shouldBe init
  }

  @Test
  def testKeywordInAddNode(): Unit = {
    val cluster = result(brokerApi.request.name(CommonUtils.randomString(10)).nodeName(nodeNames.head).create())
    result(brokerApi.start(cluster.name))

    // it's ok use keyword, but the "actual" behavior is not expected (expected addNode, but start/stop cluster)
    result(brokerApi.addNode(cluster.name, RouteUtils.START_COMMAND).flatMap(_ => brokerApi.get(cluster.name))).nodeNames shouldBe cluster.nodeNames
    result(brokerApi.addNode(cluster.name, RouteUtils.STOP_COMMAND).flatMap(_ => brokerApi.get(cluster.name))).nodeNames shouldBe cluster.nodeNames
    result(brokerApi.get(cluster.name)).state shouldBe None
  }

  @Test
  def testAddNode(): Unit = {
    val cluster = result(brokerApi.request.name(CommonUtils.randomString(10)).nodeName(nodeNames.head).create())
    result(brokerApi.start(cluster.name))

    result(brokerApi.addNode(cluster.name, nodeNames.last).flatMap(_ => brokerApi.get(cluster.name))).nodeNames shouldBe
      cluster.copy(nodeNames = cluster.nodeNames ++ Set(nodeNames.last)).nodeNames
  }

  @Test
  def testRemoveNode(): Unit = {
    val cluster = result(brokerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).create())
    result(brokerApi.start(cluster.name))

    result(brokerApi.removeNode(cluster.name, nodeNames.last))

    result(brokerApi.get(cluster.name)).nodeNames shouldBe cluster.nodeNames - nodeNames.last
  }

  @Test
  def testInvalidClusterName(): Unit =
    an[IllegalArgumentException] should be thrownBy result(brokerApi.request.name("--]").nodeNames(nodeNames).create())

  @Test
  def runMultiBkClustersOnSameZkCluster(): Unit = {
    // pass
    val bk = result(brokerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).create())
    result(brokerApi.start(bk.name))

    // we can't create multi broker clusters on same zk cluster
    val bk2 = result(brokerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).create())
    an[IllegalArgumentException] should be thrownBy result(brokerApi.start(bk2.name))
  }

  @Test
  def createBkClusterWithSameName(): Unit = {
    val name = CommonUtils.randomString(10)
    // pass
    val bk = result(brokerApi.request.name(name).nodeNames(nodeNames).create())
    result(brokerApi.start(bk.name))

    val zk2 = result(
      ZookeeperApi.access
        .hostname(configurator.hostname)
        .port(configurator.port)
        .request
        .name(CommonUtils.randomString(10))
        .nodeNames(nodeNames)
        .create()
    )

    an[IllegalArgumentException] should be thrownBy result(
      brokerApi.request.name(name).zookeeperClusterName(zk2.name).nodeNames(nodeNames).create())
  }

  @Test
  def clientPortConflict(): Unit = {
    val clientPort = CommonUtils.availablePort()
    val bk = result(
      brokerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).clientPort(clientPort).create())
    result(brokerApi.start(bk.name))

    val zk2 = result(
      ZookeeperApi.access
        .hostname(configurator.hostname)
        .port(configurator.port)
        .request
        .name(CommonUtils.randomString(10))
        .nodeNames(nodeNames)
        .create()
    )
    result(ZookeeperApi.access.hostname(configurator.hostname).port(configurator.port).start(zk2.name))

    val bk2 = result(
      brokerApi.request
        .name(CommonUtils.randomString(10))
        .nodeNames(nodeNames)
        .clientPort(clientPort)
        .zookeeperClusterName(zk2.name)
        .create())
    an[IllegalArgumentException] should be thrownBy result(brokerApi.start(bk2.name))

    // pass
    val bk3 = result(
      brokerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).zookeeperClusterName(zk2.name).create())
    result(brokerApi.start(bk3.name))
  }

  @Test
  def exporterPortConflict(): Unit = {
    val exporterPort = CommonUtils.availablePort()
    val bk = result(
      brokerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).exporterPort(exporterPort).create())
    result(brokerApi.start(bk.name))

    val zk2 = result(
      ZookeeperApi.access
        .hostname(configurator.hostname)
        .port(configurator.port)
        .request
        .name(CommonUtils.randomString(10))
        .nodeNames(nodeNames)
        .create()
    )
    result(ZookeeperApi.access.hostname(configurator.hostname).port(configurator.port).start(zk2.name))

    val bk2 = result(
      brokerApi.request
        .name(CommonUtils.randomString(10))
        .nodeNames(nodeNames)
        .exporterPort(exporterPort)
        .zookeeperClusterName(zk2.name)
        .create())
    an[IllegalArgumentException] should be thrownBy result(brokerApi.start(bk2.name))

    // pass
    val bk3 = result(
      brokerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).zookeeperClusterName(zk2.name).create())
    result(brokerApi.start(bk3.name))
  }

  @Test
  def jmxPortConflict(): Unit = {
    val jmxPort = CommonUtils.availablePort()
    val bk = result(brokerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).jmxPort(jmxPort).create())
    result(brokerApi.start(bk.name))

    val zk2 = result(
      ZookeeperApi.access
        .hostname(configurator.hostname)
        .port(configurator.port)
        .request
        .name(CommonUtils.randomString(10))
        .nodeNames(nodeNames)
        .create()
    )
    result(ZookeeperApi.access.hostname(configurator.hostname).port(configurator.port).start(zk2.name))

    val bk2 = result(
      brokerApi.request
        .name(CommonUtils.randomString(10))
        .nodeNames(nodeNames)
        .jmxPort(jmxPort)
        .zookeeperClusterName(zk2.name)
        .create())
    an[IllegalArgumentException] should be thrownBy result(brokerApi.start(bk2.name))

    // pass
    val bk3 = result(
      brokerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).zookeeperClusterName(zk2.name).create())
    result(brokerApi.start(bk3.name))
  }

  @Test
  def testForceDelete(): Unit = {
    val initialCount = configurator.clusterCollie.brokerCollie.asInstanceOf[FakeBrokerCollie].forceRemoveCount

    // graceful delete
    val bk0 = result(brokerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).create())
    result(brokerApi.start(bk0.name))
    result(brokerApi.stop(bk0.name))
    result(brokerApi.delete(bk0.name))
    configurator.clusterCollie.brokerCollie.asInstanceOf[FakeBrokerCollie].forceRemoveCount shouldBe initialCount

    // force delete
    val bk1 = result(brokerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).create())
    result(brokerApi.start(bk1.name))
    result(brokerApi.forceStop(bk1.name))
    result(brokerApi.delete(bk1.name))
    configurator.clusterCollie.brokerCollie.asInstanceOf[FakeBrokerCollie].forceRemoveCount shouldBe initialCount + 1
  }

  @Test
  def testTopicSettingDefinitions(): Unit = {
    result(brokerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).create())
    result(brokerApi.list()).size should not be 0
    result(brokerApi.list()).foreach(_.topicSettingDefinitions.size should not be 0)
  }

  @Test
  def testIdempotentStart(): Unit = {
    val bk = result(brokerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).create())
    (0 until 10).foreach(_ => result(brokerApi.start(bk.name)))
  }

  @Test
  def failToUpdateRunningBrokerCluster(): Unit = {
    val bk = result(brokerApi.request.nodeName(nodeNames.head).create())
    result(brokerApi.start(bk.name))
    an[IllegalArgumentException] should be thrownBy result(
      brokerApi.request.name(bk.name).nodeNames(nodeNames).update())
    result(brokerApi.stop(bk.name))
    result(brokerApi.request.nodeNames(nodeNames).update())
    result(brokerApi.start(bk.name))
  }

  @After
  def tearDown(): Unit = Releasable.close(configurator)
}
