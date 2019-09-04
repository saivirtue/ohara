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
import com.island.ohara.client.configurator.v0.BrokerApi.{BrokerClusterInfo, Creation}
import com.island.ohara.client.configurator.v0.ContainerApi.{ContainerInfo, PortMapping, PortPair}
import com.island.ohara.client.configurator.v0.NodeApi.Node
import com.island.ohara.client.configurator.v0.ZookeeperApi.ZookeeperClusterInfo
import com.island.ohara.client.configurator.v0.{BrokerApi, ClusterInfo, TopicApi, ZookeeperApi}
import com.island.ohara.client.kafka.TopicAdmin
import com.island.ohara.common.annotations.Optional
import com.island.ohara.common.util.CommonUtils
import com.island.ohara.metrics.BeanChannel
import com.island.ohara.metrics.kafka.TopicMeter
import spray.json.{JsString, JsValue}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

trait BrokerCollie extends Collie[BrokerClusterInfo] {

  /**
    * This is a complicated process. We must address following issues.
    * 1) check the existence of cluster
    * 2) check the existence of nodes
    * 3) Each broker container has got to export exporter port and client port
    * 4) Each broker container should assign "docker host name/port" to advertised name/port
    * 5) add zookeeper routes to all broker containers (broker needs to connect to zookeeper cluster)
    * 6) Add broker routes to all broker containers
    * 7) update existed containers (if we are adding new node into a running cluster)
    * @return creator of broker cluster
    */
  override def creator: BrokerCollie.ClusterCreator = (executionContext, creation) => {
    implicit val exec: ExecutionContext = executionContext
    clusters().flatMap(clusters => {
      clusters
        .filter(_._1.isInstanceOf[BrokerClusterInfo])
        .map {
          case (cluster, containers) => cluster.asInstanceOf[BrokerClusterInfo] -> containers
        }
        .find(_._1.name == creation.name)
        .map(_._2)
        .map(containers =>
          nodeCollie
            .nodes(containers.map(_.nodeName).toSet)
            .map(_.map(node => node -> containers.find(_.nodeName == node.name).get).toMap))
        .getOrElse(Future.successful(Map.empty))
        .map {
          existNodes =>
            // if there is a running cluster already, we should check the consistency of configuration
            existNodes.values.foreach {
              container =>
                def checkValue(previous: String, newValue: String): Unit =
                  if (previous != newValue) throw new IllegalArgumentException(s"previous:$previous new:$newValue")

                def check(key: String, newValue: String): Unit = {
                  val previous = container.environments(key)
                  if (previous != newValue) throw new IllegalArgumentException(s"previous:$previous new:$newValue")
                }

                checkValue(container.imageName, creation.imageName)
                check(BrokerApi.CLIENT_PORT_KEY, creation.clientPort.toString)
                check(BrokerApi.ZOOKEEPER_CLUSTER_NAME_KEY, creation.zookeeperClusterName.get)
            }
            existNodes
        }
        .flatMap(existNodes =>
          nodeCollie
            .nodes(creation.nodeNames)
            .map(_.map(node => node -> Collie.format(prefixKey, creation.name, serviceName)).toMap)
            .map((existNodes, _)))
        .map {
          case (existNodes, newNodes) =>
            existNodes.keys.foreach(node =>
              if (newNodes.keys.exists(_.name == node.name))
                throw new IllegalArgumentException(s"${node.name} is running on a ${creation.name}"))

            (existNodes, newNodes, zookeeperContainers(creation.zookeeperClusterName.get))
        }
        .flatMap {
          case (existNodes, newNodes, zkContainers) =>
            zkContainers
              .flatMap(zkContainers => {
                if (zkContainers.isEmpty)
                  throw new IllegalArgumentException(s"${creation.name} zookeeper container doesn't exist")
                val zookeepers = zkContainers
                  .map(c => s"${c.nodeName}:${c.environments(ZookeeperApi.CLIENT_PORT_KEY).toInt}")
                  .mkString(",")

                val route = resolveHostNames((existNodes.keys.map(_.hostname) ++ newNodes.keys
                  .map(_.hostname) ++ zkContainers.map(_.nodeName)).toSet)
                existNodes.foreach {
                  case (node, container) => hookOfNewRoute(node, container, route)
                }

                // the new broker node can't take used id so we find out the max id which is used by current cluster
                val maxId: Int =
                  if (existNodes.isEmpty) 0
                  else existNodes.values.map(_.environments(BrokerApi.ID_KEY).toInt).toSet.max + 1

                // ssh connection is slow so we submit request by multi-thread
                Future.sequence(newNodes.zipWithIndex.map {
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
                            hostPort = creation.exporterPort,
                            containerPort = creation.exporterPort
                          ),
                          PortPair(
                            hostPort = creation.jmxPort,
                            containerPort = creation.jmxPort
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
                      }
                      // each broker instance needs an unique id to identify
                        + (BrokerApi.ID_KEY -> (maxId + index).toString)
                      // connect to user defined zookeeper cluster
                        + (BrokerApi.ZOOKEEPERS_KEY -> zookeepers)
                      // expose the borker hostname for zookeeper to register
                        + (BrokerApi.ADVERTISED_HOSTNAME_KEY -> node.hostname)
                      // jmx exporter host name
                        + (BrokerApi.JMX_HOSTNAME_KEY -> node.hostname)
                      // we convert all settings to specific string in order to fetch all settings from
                      // container env quickly. Also, the specific string enable us to pick up the "true" settings
                      // from envs since there are many system-defined settings in container envs.
                        + toEnvString(creation.settings),
                      hostname = containerName
                    )
                    doCreator(executionContext, creation.name, containerName, containerInfo, node, route).map(_ =>
                      Some(containerInfo))
                })
              })
              .map(_.flatten.toSeq)
              .map {
                successfulContainers =>
                  if (successfulContainers.isEmpty)
                    throw new IllegalArgumentException(s"failed to create ${creation.name} on $serviceName")
                  val clusterInfo = BrokerClusterInfo(
                    settings = BrokerApi.access.request
                      .settings(creation.settings)
                      .nodeNames(successfulContainers.map(_.nodeName).toSet)
                      .creation
                      .settings,
                    nodeNames = (successfulContainers.map(_.nodeName) ++ existNodes.map(_._1.name)).toSet,
                    deadNodes = Set.empty,
                    state = None,
                    error = None,
                    lastModified = CommonUtils.current(),
                    topicSettingDefinitions = TopicApi.TOPIC_DEFINITIONS
                  )
                  postCreateBrokerCluster(clusterInfo, successfulContainers)
                  clusterInfo
              }
        }
    })
  }

  /**
    * Please setting nodeCollie to implement class
    * @return
    */
  protected def nodeCollie: NodeCollie

  /**
    *  Implement prefix name for the platform
    * @return
    */
  protected def prefixKey: String

  override val serviceName: String = BrokerApi.BROKER_SERVICE_NAME

  /**
    * Please implement this function to get Zookeeper cluster information
    * @param executionContext execution context
    * @return zookeeper cluster information
    */
  protected def zookeeperClusters(
    implicit executionContext: ExecutionContext): Future[Map[ClusterInfo, Seq[ContainerInfo]]]

  /**
    * Update exist node info
    * @param node node object
    * @param container container information
    * @param route ip-host mapping list
    */
  protected def hookUpdate(node: Node, container: ContainerInfo, route: Map[String, String]): Unit = {
    //Nothing
  }

  /**
    * Please implement this function to create the container to a different platform
    * @param executionContext execution context
    * @param clusterName cluster name
    * @param containerName container name
    * @param containerInfo container information
    * @param node node object
    * @param route ip-host mapping
    */
  protected def doCreator(executionContext: ExecutionContext,
                          clusterName: String,
                          containerName: String,
                          containerInfo: ContainerInfo,
                          node: Node,
                          route: Map[String, String]): Future[Unit]

  /**
    * After creating the broker, need to processor other things
    * @param clusterInfo broker cluster information
    * @param successfulContainers successful created containers
    */
  protected def postCreateBrokerCluster(clusterInfo: ClusterInfo, successfulContainers: Seq[ContainerInfo]): Unit = {
    //Default Nothing
  }

  /**
    * Create a topic admin according to passed cluster name.
    * Noted: if target cluster doesn't exist, an future with exception will return
    * @param clusterName target cluster
    * @return cluster info and topic admin
    */
  def topicAdmin(clusterName: String)(
    implicit executionContext: ExecutionContext): Future[(BrokerClusterInfo, TopicAdmin)] = cluster(clusterName).map {
    case (c, _) => (c, topicAdmin(c))
  }

  /**
    * Create a topic admin according to passed cluster.
    * @param cluster target cluster
    * @return topic admin
    */
  def topicAdmin(cluster: BrokerClusterInfo): TopicAdmin = TopicAdmin(cluster.connectionProps)

  /**
    * Get all meter beans from specific broker cluster
    * @param cluster cluster
    * @return meter beans
    */
  def topicMeters(cluster: BrokerClusterInfo): Seq[TopicMeter] = cluster.nodeNames.flatMap { node =>
    BeanChannel.builder().hostname(node).port(cluster.jmxPort).build().topicMeters().asScala
  }.toSeq

  private[agent] def toBrokerCluster(clusterName: String, containers: Seq[ContainerInfo]): Future[BrokerClusterInfo] = {
    val first = containers.head
    val creation = BrokerApi.access.request
      .settings(seekSettings(first.environments))
      .nodeNames(containers.map(_.nodeName).toSet)
      .creation
    Future.successful(
      BrokerClusterInfo(
        settings = creation.settings,
        nodeNames = creation.nodeNames,
        // Currently, docker and k8s has same naming rule for "Running",
        // it is ok that we use the containerState.RUNNING here.
        deadNodes = creation.nodeNames -- containers
          .filter(_.state == ContainerState.RUNNING.name)
          .map(_.nodeName)
          .toSet,
        state = toClusterState(containers).map(_.name),
        // TODO how could we fetch the error?...by Sam
        error = None,
        lastModified = CommonUtils.current(),
        topicSettingDefinitions = TopicApi.TOPIC_DEFINITIONS
      ))
  }

  /**
    * For check, zookeeper cluster exist. You can override this function to check zookeeper cluster info
    * @param zkClusterName zookeeper cluster name
    * @param executionContext execution context
    * @return
    */
  private def zookeeperContainers(zkClusterName: String)(
    implicit executionContext: ExecutionContext): Future[Seq[ContainerInfo]] = {
    zookeeperClusters.map(
      _.filter(_._1.isInstanceOf[ZookeeperClusterInfo])
        .find(_._1.name == zkClusterName)
        .map(_._2)
        .getOrElse(throw new NoSuchClusterException(s"zookeeper cluster:$zkClusterName doesn't exist"))
    )
  }

  /**
    * there is new route to the node. the sub class can update the running container to apply new route.
    */
  protected def hookOfNewRoute(node: Node, container: ContainerInfo, route: Map[String, String]): Unit = {
    //Nothing
  }
}

object BrokerCollie {

  trait ClusterCreator extends Collie.ClusterCreator[BrokerClusterInfo] {
    private[this] val request = BrokerApi.access.request

    override protected def doCopy(clusterInfo: BrokerClusterInfo): Unit = {
      zookeeperClusterName(clusterInfo.zookeeperClusterName)
      clientPort(clusterInfo.clientPort)
      exporterPort(clusterInfo.exporterPort)
      jmxPort(clusterInfo.jmxPort)
    }

    def zookeeperClusterName(zookeeperClusterName: String): ClusterCreator = {
      request.zookeeperClusterName(zookeeperClusterName)
      this
    }

    @Optional("default is random port")
    def clientPort(clientPort: Int): ClusterCreator = {
      request.clientPort(clientPort)
      this
    }

    @Optional("default is random port")
    def exporterPort(exporterPort: Int): ClusterCreator = {
      request.exporterPort(exporterPort)
      this
    }

    @Optional("default is random port")
    def jmxPort(jmxPort: Int): ClusterCreator = {
      request.jmxPort(jmxPort)
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

    override def create(): Future[BrokerClusterInfo] = {
      // initial the basic creation required parameters (defined in ClusterInfo) for broker
      val creation = request.name(clusterName).imageName(imageName).nodeNames(nodeNames).creation
      doCreate(
        executionContext = Objects.requireNonNull(executionContext),
        creation = creation
      )
    }

    override protected def checkClusterName(clusterName: String): String = {
      BrokerApi.BROKER_CREATION_JSON_FORMAT.check("name", JsString(clusterName))
      clusterName
    }

    protected def doCreate(executionContext: ExecutionContext, creation: Creation): Future[BrokerClusterInfo]
  }
}
