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

package com.island.ohara.it.prometheus

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.island.ohara.agent.docker.DockerClient
import com.island.ohara.agent.{ClusterCollie, NodeCollie}
import com.island.ohara.client.configurator.v0.BrokerApi.BrokerClusterInfo
import com.island.ohara.client.configurator.v0.NodeApi.Node
import com.island.ohara.client.configurator.v0.ZookeeperApi.ZookeeperClusterInfo
import com.island.ohara.client.configurator.v0.{BrokerApi, ZookeeperApi}
import com.island.ohara.common.setting.ObjectKey
import com.island.ohara.common.util.CommonUtils
import com.island.ohara.it.prometheus.PrometheusJson.{Health, Targets}
import com.island.ohara.it.{EnvTestingUtils, IntegrationTest}
import org.junit.Assume._
import org.junit.{Before, Ignore, Test}
import org.scalatest.Matchers

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.ExecutionContextExecutor
class TestPrometheus extends IntegrationTest with Matchers {

  private[this] val nodes: Seq[Node] = EnvTestingUtils.sshNodes()
  private[this] var node: Node = _
  private[this] var nodeCollie: NodeCollie = _
  private[this] var clusterCollie: ClusterCollie = _

  @Before
  def check(): Unit = if (nodes.isEmpty) skipTest(s"no available nodes are passed from env variables")
  else {
    node = nodes.head
    nodeCollie = NodeCollie(Seq(node))
    clusterCollie = ClusterCollie.builderOfSsh.nodeCollie(nodeCollie).build()
    val client =
      DockerClient.builder.user(node._user).password(node._password).hostname(node.hostname).port(node._port).build
    try {
      assumeTrue(client.imageNames().contains(PrometheusServer.IMAGE_NAME_DEFAULT))
    } finally client.close()
  }

  /**
    * test kafka can export metric
    */
  @Test
  def testExporter(): Unit = {
    startZK(zkDesc => {
      assertCluster(() => result(clusterCollie.zookeeperCollie.clusters()).keys.toSeq,
                    () => result(clusterCollie.zookeeperCollie.containers(zkDesc.key)),
                    zkDesc.name)
      startBroker(
        zkDesc.key,
        (exporterPort, bkCluster) => {
          assertCluster(() => result(clusterCollie.brokerCollie.clusters()).keys.toSeq,
                        () => result(clusterCollie.brokerCollie.containers(zkDesc.key)),
                        bkCluster.name)
          implicit val actorSystem: ActorSystem = ActorSystem(s"${classOf[PrometheusClient].getSimpleName}--system")
          implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()
          val url = "http://" + node.hostname + ":" + exporterPort + "/metrics"
          import scala.concurrent.ExecutionContext.Implicits.global
          try {
            await(
              () => {
                val txt =
                  result(Http().singleRequest(HttpRequest(HttpMethods.GET, url)).flatMap(Unmarshal(_).to[String]))
                txt.contains("kafka")
              }
            )
          } finally actorSystem.terminate()
        }
      )
    })
  }

//  val clientPort = CommonUtils.availablePort()
  def startZK(f: ZookeeperClusterInfo => Unit): Unit = {
    val clusterName = CommonUtils.randomString(10)
    val electionPort = CommonUtils.availablePort()
    val peerPort = CommonUtils.availablePort()
    val clientPort = CommonUtils.availablePort()
    val zookeeperCollie = clusterCollie.zookeeperCollie

    try f(
      result(
        zookeeperCollie.creator
          .imageName(ZookeeperApi.IMAGE_NAME_DEFAULT)
          .group(com.island.ohara.client.configurator.v0.GROUP_DEFAULT)
          .clientPort(clientPort)
          .electionPort(electionPort)
          .peerPort(peerPort)
          .name(clusterName)
          .nodeName(result(nodeCollie.nodes()).head.name)
          .create()
          .flatMap(_ => zookeeperCollie.cluster(clusterName).map(_._1))
      ))
    finally result(
      zookeeperCollie.remove(ObjectKey.of(com.island.ohara.client.configurator.v0.GROUP_DEFAULT, clusterName)))
  }

  def startBroker(zkClusterKey: ObjectKey, f: (Int, BrokerClusterInfo) => Unit): Unit = {
    val clusterName = CommonUtils.randomString(10)
    val clientPort = CommonUtils.availablePort()
    val exporterPort = CommonUtils.availablePort()
    val brokerCollie = clusterCollie.brokerCollie

    try f(
      exporterPort,
      result(
        brokerCollie.creator
          .imageName(BrokerApi.IMAGE_NAME_DEFAULT)
          .group(com.island.ohara.client.configurator.v0.GROUP_DEFAULT)
          .name(clusterName)
          .clientPort(clientPort)
          .exporterPort(exporterPort)
          .zookeeperClusterKey(zkClusterKey)
          .nodeName(result(nodeCollie.nodes()).head.name)
          .create()
          .flatMap(_ => brokerCollie.cluster(clusterName).map(_._1))
      )
    )
    finally result(
      brokerCollie.remove(ObjectKey.of(com.island.ohara.client.configurator.v0.GROUP_DEFAULT, clusterName)))
  }

  private val fakeUrl = "128.128.128.128"

  /**
    * test promethues add targets and remove targets
    *
    */
  @Ignore
  @Test
  def startPrometheus(): Unit = {
    simpleServer(ports => {
      prometheus(
        node,
        desc => {

          val client =
            DockerClient.builder
              .user(node._user)
              .password(node._password)
              .hostname(node.hostname)
              .port(node._port)
              .build
          try {
            val util = PrometheusConfigUtils(client.containerInspector(desc.name))
            val pclient = PrometheusClient(node.hostname + ":" + desc.clientPort)

            //check not in  target
            await(() => !isContain(pclient.targets(), "123.123.123.123:" + desc.clientPort))

            //check inactive target
            await(() => isActive(pclient.targets(), fakeUrl + ":" + desc.clientPort, health = false))

            //check add targets
            ports
              .map(node.hostname + ":" + _)
              .foreach(target => {
                util.addTarget(target)
                await(() => isActive(pclient.targets(), target))
              })

            //       check remove targets
            ports
              .map(node.hostname + ":" + _)
              .foreach(target => {
                util.removeTarget(target)
                await(() => !isContain(pclient.targets(), target))
              })
          } finally client.close()
        }
      )
    })
  }

  def prometheus(node: Node, f: PrometheusDescription => Unit): Unit = {
    val clientPort = CommonUtils.availablePort()
    val server = PrometheusServer.creater().clientPort(clientPort).targets(Seq(fakeUrl + ":" + clientPort)).create(node)

    try f(server.start())
    finally server.stop()
  }

  /**
    * fake akka server
    */
  def simpleServer(f: Seq[Int] => Unit): Unit = {
    implicit val system: ActorSystem = ActorSystem("my-system-")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext: ExecutionContextExecutor = system.dispatcher
    import akka.http.scaladsl.model._
    import akka.http.scaladsl.server.Directives._
    try {
      val route =
        path("metrics") {
          get {
            complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, """com_island_ohara_test{name="test"} 5.0"""))
          }
        }

      val bindingFuture = Http().bindAndHandle(route, "0.0.0.0", 0)
      val bindingFuture2 = Http().bindAndHandle(route, "0.0.0.0", 0)
      val bindingFuture3 = Http().bindAndHandle(route, "0.0.0.0", 0)
      val ports: ListBuffer[Int] = ListBuffer.empty
      bindingFuture.onComplete(_.map(_.localAddress.getPort).foreach(ports += _))
      bindingFuture2.onComplete(_.map(_.localAddress.getPort).foreach(ports += _))
      bindingFuture3.onComplete(_.map(_.localAddress.getPort).foreach(ports += _))

      f(ports.toList)
    } finally system.terminate()

  }

  /**
    * check target In targets
    */
  private def isContain(targets: Targets, target: String) = {
    targets.data.activeTargets.exists(_.discoveredLabels.__address__ == target)
  }

  /**
    * check target In targets and active
    */
  private def isActive(targets: Targets, target: String, health: Boolean = true) = {
    if (health)
      targets.data.activeTargets.exists(x => {
        x.discoveredLabels.__address__ == target && x.health == Health.UP
      })
    else
      targets.data.activeTargets.exists(x => {
        x.discoveredLabels.__address__ == target && x.health == Health.Down
      })
  }
}
