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

package com.island.ohara.agent
import java.util.Objects

import com.island.ohara.agent.docker.ContainerState
import com.island.ohara.client.configurator.v0.ContainerApi.{ContainerInfo, PortMapping, PortPair}
import com.island.ohara.client.configurator.v0.NodeApi.Node
import com.island.ohara.client.configurator.v0.ZookeeperApi.{Creation, ZookeeperClusterInfo}
import com.island.ohara.client.configurator.v0.{ClusterInfo, ZookeeperApi}
import com.island.ohara.common.annotations.Optional
import com.island.ohara.common.util.CommonUtils
import spray.json.{JsString, JsValue}

import scala.concurrent.{ExecutionContext, Future}

/**
  * An interface of controlling zookeeper cluster.
  * It isolates the implementation of container manager from Configurator.
  */
trait ZookeeperCollie extends Collie[ZookeeperClusterInfo] {

  override val serviceName: String = ZookeeperApi.ZOOKEEPER_SERVICE_NAME

  /**
    * This is a complicated process. We must address following issues.
    * 1) check the existence of cluster
    * 2) check the existence of nodes
    * 3) Each zookeeper container has got to export peer port, election port, and client port
    * 4) Each zookeeper container should use "docker host name" to replace "container host name".
    * 4) Add routes to all zookeeper containers
    * @return creator of broker cluster
    */
  override def creator: ZookeeperCollie.ClusterCreator = (executionContext, creation) => {
    implicit val exec: ExecutionContext = executionContext
    clusters().flatMap(clusters => {
      if (clusters.keys.filter(_.isInstanceOf[ZookeeperClusterInfo]).exists(_.name == creation.name))
        Future.failed(new IllegalArgumentException(s"zookeeper cluster:${creation.name} exists!"))
      else
        nodeCollie
          .nodes(creation.nodeNames)
          .map(_.map(node => node -> Collie.format(prefixKey, creation.name, serviceName)).toMap)
          .flatMap {
            nodes =>
              // add route in order to make zk node can connect to each other.
              val route: Map[String, String] = routeInfo(nodes)

              val zkServers: String = nodes.keys.map(_.name).mkString(" ")
              // ssh connection is slow so we submit request by multi-thread
              Future
                .sequence(nodes.zipWithIndex.map {
                  case ((node, containerName), index) =>
                    val containerInfo = ContainerInfo(
                      nodeName = node.name,
                      id = Collie.UNKNOWN,
                      imageName = creation.imageName,
                      created = Collie.UNKNOWN,
                      state = Collie.UNKNOWN,
                      kind = Collie.UNKNOWN,
                      name = containerName,
                      size = Collie.UNKNOWN,
                      portMappings = Seq(PortMapping(
                        hostIp = Collie.UNKNOWN,
                        portPairs = Seq(
                          PortPair(
                            hostPort = creation.clientPort,
                            containerPort = creation.clientPort
                          ),
                          PortPair(
                            hostPort = creation.peerPort,
                            containerPort = creation.peerPort
                          ),
                          PortPair(
                            hostPort = creation.electionPort,
                            containerPort = creation.electionPort
                          )
                        )
                      )),
                      environments = creation.settings.map {
                        case (k, v) =>
                          k -> (v match {
                            // the string in json representation has quote in the beginning and end.
                            // we don't like the quotes since it obstruct us to cast value to pure string.
                            case JsString(s) => s
                            // save the json string for all settings
                            case _ => CommonUtils.toEnvString(v.toString)
                          })
                        // each zookeeper instance needs an unique id to identify
                      } + (ZookeeperApi.ZK_ID_KEY -> index.toString)
                      // zookeeper cluster will use this setting to communicate to each other zookeeper instance
                        + (ZookeeperApi.SERVERS_KEY -> zkServers)
                      // we convert all settings to specific string in order to fetch all settings from
                      // container env quickly. Also, the specific string enable us to pick up the "true" settings
                      // from envs since there are many system-defined settings in container envs.
                        + toEnvString(creation.settings),
                      // zookeeper doesn't have advertised hostname/port so we assign the "docker host" directly
                      hostname = node.name
                    )
                    doCreator(executionContext, creation.name, containerName, containerInfo, node, route).map(_ =>
                      Some(containerInfo))
                })
                .map(_.flatten.toSeq)
                .map {
                  successfulContainers =>
                    if (successfulContainers.isEmpty)
                      throw new IllegalArgumentException(s"failed to create ${creation.name} on $serviceName")
                    val clusterInfo = ZookeeperClusterInfo(
                      settings = ZookeeperApi.access.request
                        .settings(creation.settings)
                        .nodeNames(successfulContainers.map(_.nodeName).toSet)
                        .creation
                        .settings,
                      nodeNames = successfulContainers.map(_.nodeName).toSet,
                      deadNodes = Set.empty,
                      // We do not care the user parameters since it's stored in configurator already
                      state = None,
                      error = None,
                      lastModified = CommonUtils.current()
                    )
                    postCreateZookeeperCluster(clusterInfo, successfulContainers)
                    clusterInfo
                }
          }
    })
  }

  /**
    * Please implement nodeCollie
    * @return
    */
  protected def nodeCollie: NodeCollie

  /**
    * The prefix name for platform
    * @return
    */
  protected def prefixKey: String

  protected def doCreator(executionContext: ExecutionContext,
                          clusterName: String,
                          containerName: String,
                          containerInfo: ContainerInfo,
                          node: Node,
                          route: Map[String, String]): Future[Unit]

  protected def postCreateZookeeperCluster(clusterInfo: ClusterInfo, successfulContainers: Seq[ContainerInfo]): Unit = {
    //Default Nothing
  }

  protected def routeInfo(nodes: Map[Node, String]): Map[String, String] =
    nodes.map {
      case (node, _) =>
        node.name -> CommonUtils.address(node.name)
    }

  private[agent] def toZookeeperCluster(clusterName: String,
                                        containers: Seq[ContainerInfo]): Future[ZookeeperClusterInfo] = {
    val first = containers.head
    val creation = ZookeeperApi.access.request.settings(seekSettings(first.environments)).creation
    Future.successful(
      ZookeeperClusterInfo(
        settings = creation.settings,
        nodeNames = creation.nodeNames,
        // Currently, docker and k8s has same naming rule for "Running",
        // it is ok that we use the containerState.RUNNING here.
        deadNodes = containers.filterNot(_.state == ContainerState.RUNNING.name).map(_.nodeName).toSet,
        // We do not care the user parameters since it's stored in configurator already
        state = toClusterState(containers).map(_.name),
        // TODO how could we fetch the error?...by Sam
        error = None,
        lastModified = CommonUtils.current()
      ))
  }
}

object ZookeeperCollie {
  trait ClusterCreator extends Collie.ClusterCreator[ZookeeperClusterInfo] {
    private[this] val request = ZookeeperApi.access.request

    override protected def doCopy(clusterInfo: ZookeeperClusterInfo): Unit = {
      clientPort(clusterInfo.clientPort)
      peerPort(clusterInfo.peerPort)
      electionPort(clusterInfo.electionPort)
    }

    @Optional("default is random port")
    def clientPort(clientPort: Int): ClusterCreator = {
      request.clientPort(clientPort)
      this
    }

    @Optional("default is random port")
    def peerPort(peerPort: Int): ClusterCreator = {
      request.peerPort(peerPort)
      this
    }

    @Optional("default is random port")
    def electionPort(electionPort: Int): ClusterCreator = {
      request.electionPort(electionPort)
      this
    }

    /**
      * add a key-value data map for container
      *
      * @param key data key
      * @param value data value
      * @return this creator
      */
    @Optional("default is empty map")
    def setting(key: String, value: JsValue): ClusterCreator = {
      request.setting(key, value)
      this
    }

    override def create(): Future[ZookeeperClusterInfo] = {
      // initial the basic creation required parameters (defined in ClusterInfo) for zookeeper
      val creation = request.name(clusterName).imageName(imageName).nodeNames(nodeNames).creation
      doCreate(
        executionContext = Objects.requireNonNull(executionContext),
        creation = creation
      )
    }

    override protected def checkClusterName(clusterName: String): String = {
      ZookeeperApi.ZOOKEEPER_CREATION_JSON_FORMAT.check("name", JsString(clusterName))
      clusterName
    }

    protected def doCreate(executionContext: ExecutionContext, creation: Creation): Future[ZookeeperClusterInfo]
  }
}
