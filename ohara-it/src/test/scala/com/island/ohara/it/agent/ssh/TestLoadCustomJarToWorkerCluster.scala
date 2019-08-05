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

package com.island.ohara.it.agent.ssh

import java.io.File
import java.util.concurrent.TimeUnit

import com.island.ohara.client.configurator.v0.NodeApi.Node
import com.island.ohara.client.configurator.v0._
import com.island.ohara.client.kafka.WorkerClient
import com.island.ohara.common.setting.ObjectKey
import com.island.ohara.common.util.Releasable
import com.island.ohara.configurator.Configurator
import com.island.ohara.it.IntegrationTest
import com.island.ohara.it.agent.{ClusterNameHolder, CollieTestUtils}
import com.island.ohara.it.connector.{DumbSinkConnector, DumbSourceConnector}
import com.typesafe.scalalogging.Logger
import org.junit.{After, Before, Test}
import org.scalatest.Matchers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
class TestLoadCustomJarToWorkerCluster extends IntegrationTest with Matchers {

  private[this] val log = Logger(classOf[TestLoadCustomJarToWorkerCluster])

  /**
    * we need to export port to enable remote node download jar from this node
    */
  private[this] val portKey = "ohara.it.port"

  /**
    * we need to export hostname to enable remote node download jar from this node
    */
  private[this] val hostnameKey: String = "ohara.it.hostname"

  private[this] val nodeCache: Seq[Node] = CollieTestUtils.nodeCache()

  private[this] val invalidHostname = "unknown"

  private[this] val invalidPort = 0

  private[this] val publicHostname: String = sys.env.getOrElse(hostnameKey, invalidHostname)

  private[this] val publicPort = sys.env.get(portKey).fold(invalidPort)(_.toInt)

  private[this] val configurator: Configurator =
    Configurator.builder.hostname(publicHostname).port(publicPort).build()

  private[this] val zkApi = ZookeeperApi.access.hostname(configurator.hostname).port(configurator.port)

  private[this] val bkApi = BrokerApi.access.hostname(configurator.hostname).port(configurator.port)

  private[this] val wkApi = WorkerApi.access.hostname(configurator.hostname).port(configurator.port)

  private[this] val fileApi = FileInfoApi.access.hostname(configurator.hostname).port(configurator.port)

  private[this] val nameHolder = new ClusterNameHolder(nodeCache)

  /**
    * used to debug. setting false to disable cleanup of containers after testing.
    */
  private[this] val cleanup = true

  @Before
  def setup(): Unit = if (nodeCache.isEmpty || publicPort == invalidPort || publicHostname == invalidHostname)
    skipTest(
      s"${CollieTestUtils.key}, $portKey and $hostnameKey don't exist so all tests in TestLoadCustomJarToWorkerCluster are ignored")
  else {

    val nodeApi = NodeApi.access.hostname(configurator.hostname).port(configurator.port)
    nodeCache.foreach { node =>
      result(
        nodeApi.request.hostname(node.hostname).port(node._port).user(node._user).password(node._password).create())
    }
  }

  @Test
  def test(): Unit = {
    val currentPath = new File(".").getCanonicalPath
    // Both jars are pre-generated. see readme in test/resources

    // jars should use "group" (here is worker name) to identify which worker cluster will use it
    val jars = result(
      Future.traverse(Seq(new File(currentPath, "build/libs/ohara-it-source.jar"),
                          new File(currentPath, "build/libs/ohara-it-sink.jar")))(file => {
        // avoid too "frequently" create group folder for same group files
        TimeUnit.SECONDS.sleep(1)
        fileApi.request.file(file).upload()
      }))

    val zkCluster = result(
      zkApi.request
        .name(nameHolder.generateClusterName())
        .nodeNames(nodeCache.map(_.name).toSet)
        .create()
        .flatMap(info => zkApi.start(info.name).flatMap(_ => zkApi.get(info.name))))
    assertCluster(() => result(zkApi.list()), zkCluster.name)
    log.info(s"zkCluster:$zkCluster")
    val bkCluster = result(
      bkApi.request
        .name(nameHolder.generateClusterName())
        .zookeeperClusterName(zkCluster.name)
        .nodeNames(nodeCache.map(_.name).toSet)
        .create()
        .flatMap(info => bkApi.start(info.name).flatMap(_ => bkApi.get(info.name))))
    assertCluster(() => result(bkApi.list()), bkCluster.name)
    log.info(s"bkCluster:$bkCluster")
    val wkCluster = result(
      wkApi.request
        .name(nameHolder.generateClusterName())
        .brokerClusterName(bkCluster.name)
        .jarKeys(jars.map(jar => ObjectKey.of(jar.group, jar.name)).toSet)
        .nodeName(nodeCache.head.name)
        .create())
    result(wkApi.start(wkCluster.name))
    assertCluster(() => result(wkApi.list()), wkCluster.name)
    // add all remaining node to the running worker cluster
    nodeCache.filterNot(n => wkCluster.nodeNames.contains(n.name)).foreach { n =>
      result(wkApi.addNode(wkCluster.name, n.name))
    }
    // make sure all workers have loaded the test-purposed connector.
    result(wkApi.list()).find(_.name == wkCluster.name).get.nodeNames.foreach { name =>
      val workerClient = WorkerClient(s"$name:${wkCluster.clientPort}")
      await(
        () =>
          try result(workerClient.plugins()).exists(_.className == classOf[DumbSinkConnector].getName)
            && result(workerClient.plugins()).exists(_.className == classOf[DumbSourceConnector].getName)
          catch {
            case _: Throwable => false
        }
      )
    }
    await(() => {
      val connectors = result(wkApi.get(wkCluster.name)).connectors
      connectors.map(_.className).contains(classOf[DumbSinkConnector].getName) &&
      connectors.map(_.className).contains(classOf[DumbSourceConnector].getName)
    })
  }

  @After
  final def tearDown(): Unit = {
    Releasable.close(configurator)
    if (cleanup) Releasable.close(nameHolder)
  }
}
