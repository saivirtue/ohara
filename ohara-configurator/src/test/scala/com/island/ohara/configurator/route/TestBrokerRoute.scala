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
import com.island.ohara.common.rule.OharaTest
import com.island.ohara.common.setting.ObjectKey
import com.island.ohara.common.util.{CommonUtils, Releasable}
import com.island.ohara.configurator.Configurator
import com.island.ohara.configurator.fake.FakeBrokerCollie
import org.junit.{After, Before, Test}
import org.scalatest.Matchers
import spray.json.{DeserializationException, JsArray, JsNumber, JsString}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
class TestBrokerRoute extends OharaTest with Matchers {
  private[this] val configurator = Configurator.builder.fake(0, 0).build()
  private[this] val brokerApi = BrokerApi.access.hostname(configurator.hostname).port(configurator.port)

  private[this] val zkKey = ObjectKey.of(CommonUtils.randomString(10), CommonUtils.randomString(10))

  private[this] val nodeNames: Set[String] = Set("n0", "n1")

  private[this] def result[T](f: Future[T]): T = Await.result(f, Duration("20 seconds"))

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
    val zk = result(
      ZookeeperApi.access
        .hostname(configurator.hostname)
        .port(configurator.port)
        .request
        .key(zkKey)
        .nodeNames(nodeNames)
        .create()
    )
    zk.key shouldBe zkKey

    // start zookeeper
    result(
      ZookeeperApi.access.hostname(configurator.hostname).port(configurator.port).start(zk.key)
    )
  }

  @Test
  def repeatedlyDelete(): Unit = {
    (0 to 10).foreach { index =>
      result(brokerApi.delete(ObjectKey.of(index.toString, index.toString)))
      result(brokerApi.removeNode(ObjectKey.of(index.toString, index.toString), index.toString))
    }
  }

  @Test
  def removeBrokerClusterUsedByWorkerCluster(): Unit = {
    val bk = result(
      brokerApi.request.name(CommonUtils.randomString(10)).zookeeperClusterKey(zkKey).nodeNames(nodeNames).create())
    result(brokerApi.start(bk.key))

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
      WorkerApi.access.hostname(configurator.hostname).port(configurator.port).start(wk.key)
    )

    val bks = result(brokerApi.list())

    bks.isEmpty shouldBe false

    // this broker cluster is used by worker cluster
    an[IllegalArgumentException] should be thrownBy result(brokerApi.stop(bk.key))

    // remove wk cluster
    result(WorkerApi.access.hostname(configurator.hostname).port(configurator.port).stop(wk.key))
    result(WorkerApi.access.hostname(configurator.hostname).port(configurator.port).delete(wk.key))

    // pass
    result(brokerApi.stop(bk.key))
    result(brokerApi.delete(bk.key))
  }

  @Test
  def testDefaultZkInMultiZkCluster(): Unit = {
    val anotherZkName = CommonUtils.randomString(10)
    val zk = result(
      ZookeeperApi.access
        .hostname(configurator.hostname)
        .port(configurator.port)
        .request
        .name(anotherZkName)
        .nodeNames(nodeNames)
        .create())
    zk.name shouldBe anotherZkName
    result(ZookeeperApi.access.hostname(configurator.hostname).port(configurator.port).start(zk.key))

    try {
      result(ZookeeperApi.access.hostname(configurator.hostname).port(configurator.port).list()).size shouldBe 2

      // there are two zk cluster so we have to assign the zk cluster...
      an[IllegalArgumentException] should be thrownBy result(
        brokerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).create()
      )

      val updated = result(
        brokerApi.request.name(CommonUtils.randomString(10)).zookeeperClusterKey(zk.key).nodeNames(nodeNames).update())
      updated.zookeeperClusterKey.name() shouldBe anotherZkName
      // after assigned, start is ok
      result(brokerApi.start(updated.key))
      result(brokerApi.stop(updated.key))
    } finally {
      result(ZookeeperApi.access.hostname(configurator.hostname).port(configurator.port).stop(zk.key))
      result(ZookeeperApi.access.hostname(configurator.hostname).port(configurator.port).delete(zk.key))
    }
  }

  @Test
  def testCreateOnNonexistentNode(): Unit = {
    val bk = result(
      brokerApi.request.name(CommonUtils.randomString(10)).nodeName(CommonUtils.randomString(10)).create()
    )
    an[IllegalArgumentException] should be thrownBy result(brokerApi.start(bk.key))
  }

  @Test
  def testDefaultZk(): Unit = {
    val bk = result(brokerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).create())
    // absent zookeeper name will be auto-filled in creation
    bk.zookeeperClusterKey shouldBe zkKey
    result(brokerApi.start(bk.key))
    result(brokerApi.get(bk.key)).zookeeperClusterKey shouldBe zkKey
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
    an[IllegalArgumentException] should be thrownBy result(brokerApi.start(bk.key))
  }

  @Test
  def testList(): Unit = {
    val init = result(brokerApi.list()).size
    val bk = result(brokerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).create())
    result(brokerApi.start(bk.key))

    val zk2 = result(
      ZookeeperApi.access
        .hostname(configurator.hostname)
        .port(configurator.port)
        .request
        .name(CommonUtils.randomString(10))
        .nodeNames(nodeNames)
        .create()
    )
    result(ZookeeperApi.access.hostname(configurator.hostname).port(configurator.port).start(zk2.key))

    val bk2 = result(
      brokerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).zookeeperClusterKey(zk2.key).create())
    result(brokerApi.start(bk2.key))

    val clusters = result(brokerApi.list())
    clusters.size shouldBe 2 + init
    clusters.exists(_.key == bk.key) shouldBe true
    clusters.exists(_.key == bk2.key) shouldBe true
  }

  @Test
  def testStop(): Unit = {
    val init = result(brokerApi.list()).size
    val cluster = result(brokerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).create())
    result(brokerApi.start(cluster.key))
    result(brokerApi.list()).size shouldBe init + 1
    result(brokerApi.stop(cluster.key))
    result(brokerApi.delete(cluster.key))
    result(brokerApi.list()).size shouldBe init
  }

  @Test
  def testRemove(): Unit = {
    val init = result(brokerApi.list()).size
    val cluster = result(brokerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).create())
    result(brokerApi.list()).size shouldBe init + 1
    result(brokerApi.delete(cluster.key))
    result(brokerApi.list()).size shouldBe init
  }

  @Test
  def testKeywordInAddNode(): Unit = {
    val cluster = result(brokerApi.request.name(CommonUtils.randomString(10)).nodeName(nodeNames.head).create())
    result(brokerApi.start(cluster.key))

    // it's ok use keyword, but the "actual" behavior is not expected (expected addNode, but start/stop cluster)
    result(brokerApi.addNode(cluster.key, START_COMMAND).flatMap(_ => brokerApi.get(cluster.key))).nodeNames shouldBe cluster.nodeNames
    result(brokerApi.addNode(cluster.key, STOP_COMMAND).flatMap(_ => brokerApi.get(cluster.key))).nodeNames shouldBe cluster.nodeNames
    result(brokerApi.get(cluster.key)).state shouldBe None
  }

  @Test
  def testAddNode(): Unit = {
    val cluster = result(brokerApi.request.name(CommonUtils.randomString(10)).nodeName(nodeNames.head).create())
    result(brokerApi.start(cluster.key))

    result(brokerApi.addNode(cluster.key, nodeNames.last).flatMap(_ => brokerApi.get(cluster.key))).nodeNames shouldBe cluster.nodeNames + nodeNames.last
  }

  @Test
  def testRemoveNode(): Unit = {
    val cluster = result(brokerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).create())
    result(brokerApi.start(cluster.key))

    result(brokerApi.removeNode(cluster.key, nodeNames.last))

    result(brokerApi.get(cluster.key)).nodeNames shouldBe cluster.nodeNames - nodeNames.last
  }

  @Test
  def testInvalidClusterName(): Unit =
    an[DeserializationException] should be thrownBy result(brokerApi.request.name("--]").nodeNames(nodeNames).create())

  @Test
  def runMultiBkClustersOnSameZkCluster(): Unit = {
    // pass
    val bk = result(brokerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).create())
    result(brokerApi.start(bk.key))

    // we can't create multi broker clusters on same zk cluster
    val bk2 = result(brokerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).create())
    an[IllegalArgumentException] should be thrownBy result(brokerApi.start(bk2.key))
  }

  @Test
  def createBkClusterWithSameName(): Unit = {
    val name = CommonUtils.randomString(10)
    // pass
    val bk = result(brokerApi.request.name(name).nodeNames(nodeNames).create())
    result(brokerApi.start(bk.key))

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
      brokerApi.request.name(name).zookeeperClusterKey(zk2.key).nodeNames(nodeNames).create())
  }

  @Test
  def clientPortConflict(): Unit = {
    val clientPort = CommonUtils.availablePort()
    val bk = result(
      brokerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).clientPort(clientPort).create())
    result(brokerApi.start(bk.key))

    val zk2 = result(
      ZookeeperApi.access
        .hostname(configurator.hostname)
        .port(configurator.port)
        .request
        .name(CommonUtils.randomString(10))
        .nodeNames(nodeNames)
        .create()
    )
    result(ZookeeperApi.access.hostname(configurator.hostname).port(configurator.port).start(zk2.key))

    val bk2 = result(
      brokerApi.request
        .name(CommonUtils.randomString(10))
        .nodeNames(nodeNames)
        .clientPort(clientPort)
        .zookeeperClusterKey(zk2.key)
        .create())
    an[IllegalArgumentException] should be thrownBy result(brokerApi.start(bk2.key))

    // pass
    val bk3 = result(
      brokerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).zookeeperClusterKey(zk2.key).create())
    result(brokerApi.start(bk3.key))
  }

  @Test
  def exporterPortConflict(): Unit = {
    val exporterPort = CommonUtils.availablePort()
    val bk = result(
      brokerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).exporterPort(exporterPort).create())
    result(brokerApi.start(bk.key))

    val zk2 = result(
      ZookeeperApi.access
        .hostname(configurator.hostname)
        .port(configurator.port)
        .request
        .name(CommonUtils.randomString(10))
        .nodeNames(nodeNames)
        .create()
    )
    result(ZookeeperApi.access.hostname(configurator.hostname).port(configurator.port).start(zk2.key))

    val bk2 = result(
      brokerApi.request
        .name(CommonUtils.randomString(10))
        .nodeNames(nodeNames)
        .exporterPort(exporterPort)
        .zookeeperClusterKey(zk2.key)
        .create())
    an[IllegalArgumentException] should be thrownBy result(brokerApi.start(bk2.key))

    // pass
    val bk3 = result(
      brokerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).zookeeperClusterKey(zk2.key).create())
    result(brokerApi.start(bk3.key))
  }

  @Test
  def jmxPortConflict(): Unit = {
    val jmxPort = CommonUtils.availablePort()
    val bk = result(brokerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).jmxPort(jmxPort).create())
    result(brokerApi.start(bk.key))

    val zk2 = result(
      ZookeeperApi.access
        .hostname(configurator.hostname)
        .port(configurator.port)
        .request
        .name(CommonUtils.randomString(10))
        .nodeNames(nodeNames)
        .create()
    )
    result(ZookeeperApi.access.hostname(configurator.hostname).port(configurator.port).start(zk2.key))

    val bk2 = result(
      brokerApi.request
        .name(CommonUtils.randomString(10))
        .nodeNames(nodeNames)
        .jmxPort(jmxPort)
        .zookeeperClusterKey(zk2.key)
        .create())
    an[IllegalArgumentException] should be thrownBy result(brokerApi.start(bk2.key))

    // pass
    val bk3 = result(
      brokerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).zookeeperClusterKey(zk2.key).create())
    result(brokerApi.start(bk3.key))
  }

  @Test
  def testForceDelete(): Unit = {
    val initialCount = configurator.clusterCollie.brokerCollie.asInstanceOf[FakeBrokerCollie].forceRemoveCount

    // graceful delete
    val bk0 = result(brokerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).create())
    result(brokerApi.start(bk0.key))
    result(brokerApi.stop(bk0.key))
    result(brokerApi.delete(bk0.key))
    configurator.clusterCollie.brokerCollie.asInstanceOf[FakeBrokerCollie].forceRemoveCount shouldBe initialCount

    // force delete
    val bk1 = result(brokerApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).create())
    result(brokerApi.start(bk1.key))
    result(brokerApi.forceStop(bk1.key))
    result(brokerApi.delete(bk1.key))
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
    (0 until 10).foreach(_ => result(brokerApi.start(bk.key)))
  }

  @Test
  def failToUpdateRunningBrokerCluster(): Unit = {
    val bk = result(brokerApi.request.nodeName(nodeNames.head).create())
    result(brokerApi.start(bk.key))
    an[IllegalArgumentException] should be thrownBy result(
      brokerApi.request.name(bk.name).nodeNames(nodeNames).update())
    result(brokerApi.stop(bk.key))
    result(brokerApi.request.name(bk.name).nodeNames(nodeNames).update())
    result(brokerApi.start(bk.key))
  }

  @Test
  def testCustomTagsShouldExistAfterRunning(): Unit = {
    val tags = Map(
      "aa" -> JsString("bb"),
      "cc" -> JsNumber(123),
      "dd" -> JsArray(JsString("bar"), JsString("foo"))
    )
    val bk = result(brokerApi.request.tags(tags).nodeNames(nodeNames).create())
    bk.tags shouldBe tags

    // after create, tags should exist
    result(brokerApi.get(bk.key)).tags shouldBe tags

    // after start, tags should still exist
    result(brokerApi.start(bk.key))
    result(brokerApi.get(bk.key)).tags shouldBe tags

    // after stop, tags should still exist
    result(brokerApi.stop(bk.key))
    result(brokerApi.get(bk.key)).tags shouldBe tags
  }

  @Test
  def testGroup(): Unit = {
    val group = CommonUtils.randomString(10)
    // different name but same group
    result(brokerApi.request.group(group).nodeNames(nodeNames).create()).group shouldBe group
    result(brokerApi.request.group(group).nodeNames(nodeNames).create()).group shouldBe group

    result(brokerApi.list()).size shouldBe 2

    // same name but different group
    val name = CommonUtils.randomString(10)
    val bk1 = result(brokerApi.request.name(name).nodeNames(nodeNames).create())
    bk1.name shouldBe name
    bk1.group should not be group
    val bk2 = result(brokerApi.request.name(name).group(group).nodeNames(nodeNames).create())
    bk2.name shouldBe name
    bk2.group shouldBe group

    result(brokerApi.list()).size shouldBe 4
  }

  @After
  def tearDown(): Unit = Releasable.close(configurator)
}
