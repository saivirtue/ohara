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

import com.island.ohara.client.configurator.v0.{BrokerApi, NodeApi, ZookeeperApi}
import com.island.ohara.common.rule.MediumTest
import com.island.ohara.common.util.{CommonUtils, Releasable}
import com.island.ohara.configurator.Configurator
import com.island.ohara.configurator.fake.FakeZookeeperCollie
import org.junit.{After, Before, Test}
import org.scalatest.Matchers

import scala.concurrent.ExecutionContext.Implicits.global
class TestZookeeperRoute extends MediumTest with Matchers {
  private[this] val numberOfCluster = 1
  private[this] val configurator = Configurator.builder.fake(numberOfCluster, 0).build()

  /**
    * a fake cluster has 3 fake node.
    */
  private[this] val numberOfDefaultNodes = 3 * numberOfCluster
  private[this] val zookeeperApi = ZookeeperApi.access.hostname(configurator.hostname).port(configurator.port)

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
      result(zookeeperApi.delete(index.toString))
    }
  }

  @Test
  def removeZookeeperClusterUsedByBrokerCluster(): Unit = {
    val zks = result(zookeeperApi.list())
    val bks = result(BrokerApi.access.hostname(configurator.hostname).port(configurator.port).list())
    System.out.println(zks)
    System.out.println(bks)
    // we have a default zk cluster
    zks.isEmpty shouldBe false

    val zk = zks.head

    // this zookeeper cluster is used by broker cluster
    an[IllegalArgumentException] should be thrownBy result(zookeeperApi.stop(zk.name))

    // remove all broker clusters
    result(BrokerApi.access.hostname(configurator.hostname).port(configurator.port).list())
      .map(_.name)
      .foreach(name =>
        result(BrokerApi.access.hostname(configurator.hostname).port(configurator.port).stop(name)
          flatMap (_ => BrokerApi.access.hostname(configurator.hostname).port(configurator.port).delete(name))))

    // pass
    result(zookeeperApi.stop(zk.name))
    result(zookeeperApi.delete(zk.name))
  }

  @Test
  def testCreateOnNonexistentNode(): Unit = {
    val zk = result(
      zookeeperApi.request.name(CommonUtils.randomString(10)).nodeName(CommonUtils.randomString(10)).create()
    )

    an[IllegalArgumentException] should be thrownBy result(zookeeperApi.start(zk.name))
  }

  @Test
  def testImageName(): Unit = {
    // pass by default image
    val zk = result(
      zookeeperApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).create()
    )
    result(zookeeperApi.start(zk.name))
    result(zookeeperApi.get(zk.name)).imageName shouldBe ZookeeperApi.IMAGE_NAME_DEFAULT

    // in fake mode only IMAGE_NAME_DEFAULT is supported
    val p = result(
      zookeeperApi.request
        .name(CommonUtils.randomString(10))
        .imageName(CommonUtils.randomString(10))
        .nodeNames(nodeNames)
        .create())
    an[IllegalArgumentException] should be thrownBy result(
      zookeeperApi.start(p.name)
    )
  }

  @Test
  def testList(): Unit = {
    val init = result(zookeeperApi.list()).size
    val count = 3
    (0 until count).foreach { _ =>
      result(
        zookeeperApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).create()
      )
    }
    result(zookeeperApi.list()).size shouldBe count + init
  }

  @Test
  def testDelete(): Unit = {
    val init = result(zookeeperApi.list()).size
    val cluster = result(zookeeperApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).create())
    result(zookeeperApi.list()).size shouldBe init + 1
    result(zookeeperApi.delete(cluster.name))
    result(zookeeperApi.list()).size shouldBe init
  }

  @Test
  def testStop(): Unit = {
    val init = result(zookeeperApi.list()).size
    val cluster = result(zookeeperApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).create())
    result(zookeeperApi.start(cluster.name))
    result(zookeeperApi.list()).size shouldBe init + 1
    result(zookeeperApi.stop(cluster.name))
    result(zookeeperApi.delete(cluster.name))
    result(zookeeperApi.list()).size shouldBe init
  }

  @Test
  def testAddNode(): Unit = {
    val zk = result(
      zookeeperApi.request.name(CommonUtils.randomString(10)).nodeName(nodeNames.head).create()
    )
    result(zookeeperApi.start(zk.name))
    zk.nodeNames.size shouldBe 1
    zk.nodeNames.head shouldBe nodeNames.head
    // we don't support to add zk node at runtime
    an[IllegalArgumentException] should be thrownBy result(zookeeperApi.addNode(zk.name, nodeNames.last))
  }

  @Test
  def testRemoveNode(): Unit = {
    val zk = result(
      zookeeperApi.request.name(CommonUtils.randomString(10)).nodeNames(nodeNames).create()
    )
    result(zookeeperApi.start(zk.name))
    // we don't support to remove zk node at runtime
    an[IllegalArgumentException] should be thrownBy result(zookeeperApi.removeNode(zk.name, nodeNames.head))
  }

  @Test
  def testInvalidClusterName(): Unit =
    an[IllegalArgumentException] should be thrownBy result(
      zookeeperApi.request.name("abc def").nodeNames(nodeNames).create()
    )

  @Test
  def createZkClusterWithSameName(): Unit = {
    val name = CommonUtils.randomString(10)
    result(
      zookeeperApi.request.name(name).nodeNames(nodeNames).create()
    )

    an[IllegalArgumentException] should be thrownBy result(
      zookeeperApi.request.name(name).nodeNames(nodeNames).create()
    )
  }

  @Test
  def clientPortConflict(): Unit = {
    val clientPort = CommonUtils.availablePort()
    val zk = result(
      zookeeperApi.request.name(CommonUtils.randomString(10)).clientPort(clientPort).nodeNames(nodeNames).create()
    )
    result(zookeeperApi.start(zk.name))

    val zk2 = result(
      zookeeperApi.request.name(CommonUtils.randomString(10)).clientPort(clientPort).nodeNames(nodeNames).create()
    )
    an[IllegalArgumentException] should be thrownBy result(zookeeperApi.start(zk2.name))
  }

  @Test
  def peerPortConflict(): Unit = {
    val peerPort = CommonUtils.availablePort()
    val zk = result(
      zookeeperApi.request.name(CommonUtils.randomString(10)).peerPort(peerPort).nodeNames(nodeNames).create()
    )
    result(zookeeperApi.start(zk.name))

    val zk2 = result(
      zookeeperApi.request.name(CommonUtils.randomString(10)).peerPort(peerPort).nodeNames(nodeNames).create()
    )
    an[IllegalArgumentException] should be thrownBy result(zookeeperApi.start(zk2.name))
  }

  @Test
  def electionPortConflict(): Unit = {
    val electionPort = CommonUtils.availablePort()
    val zk = result(
      zookeeperApi.request.name(CommonUtils.randomString(10)).electionPort(electionPort).nodeNames(nodeNames).create()
    )
    result(zookeeperApi.start(zk.name))

    val zk2 = result(
      zookeeperApi.request.name(CommonUtils.randomString(10)).electionPort(electionPort).nodeNames(nodeNames).create()
    )
    an[IllegalArgumentException] should be thrownBy result(zookeeperApi.start(zk2.name))
  }

  @Test
  def testIdempotentStart(): Unit = {
    val zk = result(
      zookeeperApi.request.nodeNames(nodeNames).create()
    )
    result(zookeeperApi.start(zk.name))
    val info1 = result(zookeeperApi.get(zk.name))
    // duplicated start will return the current cluster info
    result(zookeeperApi.start(zk.name))
    val info2 = result(zookeeperApi.get(zk.name))
    info1.name shouldBe info2.name
    info1.imageName shouldBe info2.imageName
    info1.nodeNames shouldBe info2.nodeNames
    info1.state shouldBe info2.state
    info1.error shouldBe info2.error
    info1.electionPort shouldBe info2.electionPort

    // we could graceful stop zookeeper
    result(zookeeperApi.stop(zk.name))
    // stop should be idempotent
    result(zookeeperApi.stop(zk.name))
    // delete should be idempotent also
    result(zookeeperApi.delete(zk.name))
    result(zookeeperApi.delete(zk.name))
    // after delete, stop will cause NoSuchElement exception
    an[IllegalArgumentException] should be thrownBy result(zookeeperApi.stop(zk.name))
  }

  @Test
  def testForceStop(): Unit = {
    val initialCount = configurator.clusterCollie.zookeeperCollie.asInstanceOf[FakeZookeeperCollie].forceRemoveCount
    val name = CommonUtils.randomString(10)
    // graceful stop
    result(
      zookeeperApi.request.name(name).nodeNames(nodeNames).create()
    )
    result(zookeeperApi.start(name))
    result(zookeeperApi.stop(name))
    result(zookeeperApi.delete(name))
    configurator.clusterCollie.zookeeperCollie.asInstanceOf[FakeZookeeperCollie].forceRemoveCount shouldBe initialCount

    // force stop
    result(
      zookeeperApi.request.name(name).nodeNames(nodeNames).create()
    )
    result(zookeeperApi.start(name))
    result(zookeeperApi.forceStop(name))
    result(zookeeperApi.delete(name))
    configurator.clusterCollie.zookeeperCollie
      .asInstanceOf[FakeZookeeperCollie]
      .forceRemoveCount shouldBe initialCount + 1
  }

  @Test
  def failToUpdateRunningZookeeperCluster(): Unit = {
    val zk = result(zookeeperApi.request.nodeName(nodeNames.head).create())
    result(zookeeperApi.start(zk.name))
    an[IllegalArgumentException] should be thrownBy result(
      zookeeperApi.request.name(zk.name).nodeNames(nodeNames).update())
    result(zookeeperApi.stop(zk.name))
    result(zookeeperApi.request.nodeNames(nodeNames).update())
    result(zookeeperApi.start(zk.name))
  }

  @After
  def tearDown(): Unit = Releasable.close(configurator)
}
