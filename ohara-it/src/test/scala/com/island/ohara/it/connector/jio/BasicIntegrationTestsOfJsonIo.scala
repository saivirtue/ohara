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

package com.island.ohara.it.connector.jio

import com.island.ohara.client.configurator.v0.{BrokerApi, NodeApi, WorkerApi, ZookeeperApi}
import com.island.ohara.client.kafka.WorkerClient
import com.island.ohara.common.util.{CommonUtils, Releasable}
import com.island.ohara.configurator.Configurator
import com.island.ohara.it.IntegrationTest
import com.island.ohara.it.agent.ClusterNameHolder
import org.junit.{After, Before}
import org.scalatest.Matchers

import scala.concurrent.ExecutionContext.Implicits.global

/**
  * the test cases are placed at BasicTestsOfJsonIn, and this abstract class is used to implements the required methods
  * by the "true"env. The env is abstract since there are two "envs" to ohara - ssh and k8s.
  */
abstract class BasicIntegrationTestsOfJsonIo extends IntegrationTest with Matchers {
  protected def configurator: Configurator
  protected def nameHolder: ClusterNameHolder
  protected val freePort: Int = CommonUtils.availablePort()

  private[this] def zkApi: ZookeeperApi.Access =
    ZookeeperApi.access.hostname(configurator.hostname).port(configurator.port)
  private[this] def bkApi: BrokerApi.Access = BrokerApi.access.hostname(configurator.hostname).port(configurator.port)
  private[this] def wkApi: WorkerApi.Access = WorkerApi.access.hostname(configurator.hostname).port(configurator.port)

  protected var _workerClient: WorkerClient = _

  protected var _brokersConnProps: String = _
  @Before
  def setup(): Unit = {
    val nodes = result(NodeApi.access.hostname(configurator.hostname).port(configurator.port).list())
    if (nodes.isEmpty) skipTest("are you kidding me? where is the nodes???")
    else {
      val zkName = nameHolder.generateClusterName()
      val zkKey = result(
        zkApi.request
          .name(zkName)
          .nodeName(nodes.head.hostname)
          .create()
          .map(_.key)
          .flatMap(key => zkApi.start(key).map(_ => key)))
      await { () =>
        try result(zkApi.get(zkKey)).state.map(_.toLowerCase).contains("running")
        catch {
          case _: Throwable => false
        }
      }

      val bkName = nameHolder.generateClusterName()
      val bkKey = result(
        bkApi.request
          .name(bkName)
          .zookeeperClusterKey(zkKey)
          .nodeName(nodes.head.hostname)
          .create()
          .map(_.key)
          .flatMap(key => bkApi.start(key).map(_ => key)))
      await { () =>
        try result(bkApi.get(bkKey)).state.map(_.toLowerCase).contains("running")
        catch {
          case _: Throwable => false
        }
      }

      val wkName = nameHolder.generateClusterName()
      val wkKey = result(
        wkApi.request
          .name(wkName)
          .brokerClusterName(bkName)
          .nodeName(nodes.head.hostname)
          .freePort(freePort)
          .create()
          .map(_.key)
          .flatMap(key => wkApi.start(key).map(_ => key)))
      await { () =>
        try result(wkApi.get(wkKey)).state.map(_.toLowerCase).contains("running")
        catch {
          case _: Throwable => false
        }
      }
      val wkCluster = result(wkApi.list()).head
      wkCluster.freePorts shouldBe Set(freePort)
      _workerClient = WorkerClient(result(wkApi.list()).head.connectionProps)
      _brokersConnProps = result(bkApi.list()).head.connectionProps
      // wait worker cluster
      await { () =>
        try result(_workerClient.plugins()).nonEmpty
        catch {
          case _: Throwable => false
        }
      }
    }
  }

  @After
  def cleanupContainers(): Unit = {
    // we don't need to cleanup connectors since we directly delete all containers :)
    Releasable.close(configurator)
    Releasable.close(nameHolder)
  }
}
