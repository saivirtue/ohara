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

import com.island.ohara.client.configurator.v0._
import com.island.ohara.common.rule.OharaTest
import com.island.ohara.common.setting.ObjectKey
import com.island.ohara.common.util.{CommonUtils, Releasable}
import com.island.ohara.configurator.Configurator
import org.junit.{After, Before, Test}
import org.scalatest.Matchers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration

class TestContainerRoute extends OharaTest with Matchers {
  private[this] val configurator = Configurator.builder.fake(0, 0).build()
  private[this] val containerApi = ContainerApi.access.hostname(configurator.hostname).port(configurator.port)
  private[this] val brokerApi = BrokerApi.access.hostname(configurator.hostname).port(configurator.port)
  private[this] val workerApi = WorkerApi.access.hostname(configurator.hostname).port(configurator.port)

  private[this] val zkClusterKey = ObjectKey.of("default", CommonUtils.randomString(10))
  private[this] val bkClusterKey = ObjectKey.of("default", CommonUtils.randomString(10))
  private[this] val wkClusterKey = ObjectKey.of("default", CommonUtils.randomString(10))

  private[this] val nodeNames: Set[String] = Set("n0", "n1")

  private[this] def result[T](f: Future[T]): T = Await.result(f, Duration("20 seconds"))
  @Before
  def setup(): Unit = {
    val nodeApi = NodeApi.access.hostname(configurator.hostname).port(configurator.port)

    nodeNames.isEmpty shouldBe false
    nodeNames.foreach { n =>
      result(nodeApi.request.hostname(n).port(22).user("user").password("pwd").create())
    }

    val zk = result(
      ZookeeperApi.access
        .hostname(configurator.hostname)
        .port(configurator.port)
        .request
        .key(zkClusterKey)
        .nodeNames(nodeNames)
        .create())
    zk.key shouldBe zkClusterKey
    result(ZookeeperApi.access.hostname(configurator.hostname).port(configurator.port).start(zk.key))

    val bk = result(brokerApi.request.key(bkClusterKey).zookeeperClusterKey(zkClusterKey).nodeNames(nodeNames).create())
    result(brokerApi.start(bk.key))

    val wk = result(
      workerApi.request.key(wkClusterKey).brokerClusterName(bkClusterKey.name()).nodeNames(nodeNames).create())
    result(workerApi.start(wk.key))
  }

  @Test
  def testGetContainersOfZookeeperCluster(): Unit = {
    val containerGroups = result(containerApi.get(zkClusterKey))
    containerGroups.size should not be 0
    containerGroups.foreach(group => {
      group.clusterKey shouldBe zkClusterKey
      group.clusterType shouldBe "zookeeper"
      group.containers.size should not be 0
    })
  }

  @Test
  def testGetContainersOfBrokerCluster(): Unit = {
    val containerGroups = result(containerApi.get(bkClusterKey))
    containerGroups.size should not be 0
    containerGroups.foreach(group => {
      group.clusterKey shouldBe bkClusterKey
      group.clusterType shouldBe "broker"
      group.containers.size should not be 0
    })
  }

  @Test
  def testGetContainersOfWorkerCluster(): Unit = {
    val containerGroups = result(containerApi.get(wkClusterKey))
    containerGroups.size should not be 0
    containerGroups.foreach(group => {
      group.clusterKey shouldBe wkClusterKey
      group.clusterType shouldBe "worker"
      group.containers.size should not be 0
    })
  }

  @After
  def tearDown(): Unit = Releasable.close(configurator)
}
