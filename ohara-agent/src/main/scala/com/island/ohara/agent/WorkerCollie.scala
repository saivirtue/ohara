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
import com.island.ohara.client.configurator.v0.BrokerApi.BrokerClusterInfo
import com.island.ohara.client.configurator.v0.ContainerApi.{ContainerInfo, PortMapping, PortPair}
import com.island.ohara.client.configurator.v0.FileInfoApi.{FileInfo, _}
import com.island.ohara.client.configurator.v0.NodeApi.Node
import com.island.ohara.client.configurator.v0.WorkerApi.WorkerClusterInfo
import com.island.ohara.client.configurator.v0.{ClusterInfo, Definition, WorkerApi}
import com.island.ohara.client.kafka.WorkerClient
import com.island.ohara.common.annotations.Optional
import com.island.ohara.common.util.CommonUtils
import com.island.ohara.metrics.BeanChannel
import com.island.ohara.metrics.basic.CounterMBean
import spray.json.{JsArray, JsString}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
trait WorkerCollie extends Collie[WorkerClusterInfo, WorkerCollie.ClusterCreator] {

  /**
    * This is a complicated process. We must address following issues.
    * 1) check the existence of cluster
    * 2) check the existence of nodes
    * 3) Each worker container has got to export exporter port and client port
    * 4) Each worker container should assign "docker host name/port" to advertised name/port
    * 5) add broker routes to all worker containers (worker needs to connect to broker cluster)
    * 6) Add worker routes to all worker containers
    * 7) update existed containers (if we are adding new node into a running cluster)
    * @return description of worker cluster
    */
  override def creator: WorkerCollie.ClusterCreator = (executionContext,
                                                       clusterName,
                                                       imageName,
                                                       brokerClusterName,
                                                       clientPort,
                                                       jmxPort,
                                                       groupId,
                                                       offsetTopicName,
                                                       offsetTopicReplications,
                                                       offsetTopicPartitions,
                                                       statusTopicName,
                                                       statusTopicReplications,
                                                       statusTopicPartitions,
                                                       configTopicName,
                                                       configTopicReplications,
                                                       jarInfos,
                                                       nodeNames) => {
    implicit val exec: ExecutionContext = executionContext
    clusters().flatMap(clusters => {
      clusters
        .filter(_._1.isInstanceOf[WorkerClusterInfo])
        .map {
          case (cluster, containers) => cluster.asInstanceOf[WorkerClusterInfo] -> containers
        }
        .find(_._1.name == clusterName)
        .map(_._2)
        .map(containers =>
          nodeCollie
            .nodes(containers.map(_.nodeName).toSet)
            .map(_.map(node => node -> containers.find(_.nodeName == node.name).get).toMap))
        .getOrElse(Future.successful(Map.empty))
        .flatMap(existNodes =>
          nodeCollie
            .nodes(nodeNames)
            .map(_.map(node => node -> ContainerCollie.format(prefixKey, clusterName, serviceName)).toMap)
            .map((existNodes, _)))
        .map {
          case (existNodes, newNodes) =>
            existNodes.keys.foreach(node =>
              if (newNodes.keys.exists(_.name == node.name))
                throw new IllegalArgumentException(s"${node.name} has run the worker service for $clusterName"))

            (existNodes, newNodes, brokerContainers(brokerClusterName))
        }
        .flatMap {
          case (existNodes, newNodes, brokerContainers) =>
            brokerContainers.flatMap(brokerContainers => {

              if (brokerContainers.isEmpty)
                throw new IllegalArgumentException(s"broker cluster:$brokerClusterName doesn't exist")
              val brokers = brokerContainers
                .map(c => s"${c.nodeName}:${c.environments(BrokerCollie.CLIENT_PORT_KEY).toInt}")
                .mkString(",")

              val route = ContainerCollie.preSettingEnvironment(existNodes.asInstanceOf[Map[Node, ContainerInfo]],
                                                                newNodes.asInstanceOf[Map[Node, String]],
                                                                brokerContainers,
                                                                resolveHostName,
                                                                hookUpdate)

              // ssh connection is slow so we submit request by multi-thread
              Future
                .sequence(newNodes.map {
                  case (node, containerName) =>
                    val containerInfo = ContainerInfo(
                      nodeName = node.name,
                      id = ContainerCollie.UNKNOWN,
                      imageName = imageName,
                      created = ContainerCollie.UNKNOWN,
                      state = ContainerCollie.UNKNOWN,
                      kind = ContainerCollie.UNKNOWN,
                      name = containerName,
                      size = ContainerCollie.UNKNOWN,
                      portMappings = Seq(
                        PortMapping(
                          hostIp = ContainerCollie.UNKNOWN,
                          portPairs = Seq(PortPair(
                                            hostPort = clientPort,
                                            containerPort = clientPort
                                          ),
                                          PortPair(
                                            hostPort = jmxPort,
                                            containerPort = jmxPort
                                          ))
                        )),
                      environments = Map(
                        WorkerCollie.CLIENT_PORT_KEY -> clientPort.toString,
                        WorkerCollie.BROKERS_KEY -> brokers,
                        WorkerCollie.GROUP_ID_KEY -> groupId,
                        WorkerCollie.OFFSET_TOPIC_KEY -> offsetTopicName,
                        WorkerCollie.OFFSET_TOPIC_PARTITIONS_KEY -> offsetTopicPartitions.toString,
                        WorkerCollie.OFFSET_TOPIC_REPLICATIONS_KEY -> offsetTopicReplications.toString,
                        WorkerCollie.CONFIG_TOPIC_KEY -> configTopicName,
                        WorkerCollie.CONFIG_TOPIC_REPLICATIONS_KEY -> configTopicReplications.toString,
                        WorkerCollie.STATUS_TOPIC_KEY -> statusTopicName,
                        WorkerCollie.STATUS_TOPIC_PARTITIONS_KEY -> statusTopicPartitions.toString,
                        WorkerCollie.STATUS_TOPIC_REPLICATIONS_KEY -> statusTopicReplications.toString,
                        WorkerCollie.ADVERTISED_HOSTNAME_KEY -> node.name,
                        WorkerCollie.ADVERTISED_CLIENT_PORT_KEY -> clientPort.toString,
                        WorkerCollie.BROKER_CLUSTER_NAME -> brokerClusterName,
                        WorkerCollie.JMX_HOSTNAME_KEY -> node.name,
                        WorkerCollie.JMX_PORT_KEY -> jmxPort.toString
                      ) ++ WorkerCollie.toMap(jarInfos),
                      hostname = containerName
                    )
                    doCreator(executionContext, clusterName, containerName, containerInfo, node, route).map(_ =>
                      Some(containerInfo))
                })
                .map(_.flatten.toSeq)
                .map {
                  successfulContainers =>
                    if (successfulContainers.isEmpty)
                      throw new IllegalArgumentException(s"failed to create $clusterName on $serviceName")
                    val clusterInfo = WorkerClusterInfo(
                      name = clusterName,
                      imageName = imageName,
                      brokerClusterName = brokerClusterName,
                      clientPort = clientPort,
                      jmxPort = jmxPort,
                      groupId = groupId,
                      offsetTopicName = offsetTopicName,
                      offsetTopicPartitions = offsetTopicPartitions,
                      offsetTopicReplications = offsetTopicReplications,
                      configTopicName = configTopicName,
                      configTopicPartitions = 1,
                      configTopicReplications = configTopicReplications,
                      statusTopicName = statusTopicName,
                      statusTopicPartitions = statusTopicPartitions,
                      statusTopicReplications = statusTopicReplications,
                      jarInfos = jarInfos,
                      connectors = Seq.empty,
                      nodeNames = (successfulContainers.map(_.nodeName) ++ existNodes.map(_._1.name)).toSet,
                      deadNodes = Set.empty,
                      // We do not care the user parameters since it's stored in configurator already
                      tags = Map.empty,
                      state = None,
                      error = None,
                      lastModified = CommonUtils.current()
                    )
                    postCreateWorkerCluster(clusterInfo, successfulContainers)
                    clusterInfo
                }
            })
        }
    })
  }

  /**
    * Please implement nodeCollie
    */
  protected def nodeCollie: NodeCollie

  /**
    * Implement prefix name for paltform
    */
  protected def prefixKey: String

  /**
    * return service name
    */
  protected def serviceName: String

  /**
    * Please implement this function to get Broker cluster information
    */
  protected def brokerClusters(
    implicit executionContext: ExecutionContext): Future[Map[ClusterInfo, Seq[ContainerInfo]]]

  /**
    * Update exist node info
    */
  protected def hookUpdate(node: Node, container: ContainerInfo, route: Map[String, String]): Unit = {
    //Nothing
  }

  /**
    * Hostname resolve to IP address
    */
  protected def resolveHostName(nodeName: String): String = {
    CommonUtils.address(nodeName)
  }

  /**
    * Please implement this function to create the container to a different platform
    */
  protected def doCreator(executionContext: ExecutionContext,
                          clusterName: String,
                          containerName: String,
                          containerInfo: ContainerInfo,
                          node: Node,
                          route: Map[String, String]): Future[Unit]

  /**
    * After the worker container creates complete, you maybe need to do other things.
    */
  protected def postCreateWorkerCluster(clusterInfo: ClusterInfo, successfulContainers: Seq[ContainerInfo]): Unit = {
    //Default Nothing
  }

  /**
    * Create a worker client according to passed cluster name.
    * Noted: if target cluster doesn't exist, an future with exception will return
    * @param clusterName target cluster
    * @return cluster info and client
    */
  def workerClient(clusterName: String)(
    implicit executionContext: ExecutionContext): Future[(WorkerClusterInfo, WorkerClient)] = cluster(clusterName).map {
    case (c, _) => (c, workerClient(c))
  }

  /**
    * Create a worker client according to passed cluster.
    * @param cluster target cluster
    * @return worker client
    */
  def workerClient(cluster: WorkerClusterInfo): WorkerClient = WorkerClient(cluster.connectionProps)

  /**
    * Get all counter beans from specific worker cluster
    * @param clusterName cluster name
    * @param executionContext thread pool
    * @return counter beans
    */
  def counters(clusterName: String)(implicit executionContext: ExecutionContext): Future[Seq[CounterMBean]] =
    cluster(clusterName).map(_._1).map(counters)

  /**
    * Get all counter beans from specific worker cluster
    * @param cluster cluster
    * @return counter beans
    */
  def counters(cluster: WorkerClusterInfo): Seq[CounterMBean] = cluster.nodeNames.flatMap { node =>
    BeanChannel.builder().hostname(node).port(cluster.jmxPort).build().counterMBeans().asScala
  }.toSeq

  private[agent] def toWorkerCluster(clusterName: String, containers: Seq[ContainerInfo])(
    implicit executionContext: ExecutionContext): Future[WorkerClusterInfo] = {
    val port = containers.head.environments(WorkerCollie.CLIENT_PORT_KEY).toInt
    connectors(containers.map(c => s"${c.nodeName}:$port").mkString(",")).map { connectors =>
      WorkerClusterInfo(
        name = clusterName,
        imageName = containers.head.imageName,
        brokerClusterName = containers.head.environments(WorkerCollie.BROKER_CLUSTER_NAME),
        clientPort = port,
        jmxPort = containers.head.environments(WorkerCollie.JMX_PORT_KEY).toInt,
        groupId = containers.head.environments(WorkerCollie.GROUP_ID_KEY),
        offsetTopicName = containers.head.environments(WorkerCollie.OFFSET_TOPIC_KEY),
        offsetTopicPartitions = containers.head.environments(WorkerCollie.OFFSET_TOPIC_PARTITIONS_KEY).toInt,
        offsetTopicReplications = containers.head.environments(WorkerCollie.OFFSET_TOPIC_REPLICATIONS_KEY).toShort,
        configTopicName = containers.head.environments(WorkerCollie.CONFIG_TOPIC_KEY),
        configTopicPartitions = 1,
        configTopicReplications = containers.head.environments(WorkerCollie.CONFIG_TOPIC_REPLICATIONS_KEY).toShort,
        statusTopicName = containers.head.environments(WorkerCollie.STATUS_TOPIC_KEY),
        statusTopicPartitions = containers.head.environments(WorkerCollie.STATUS_TOPIC_PARTITIONS_KEY).toInt,
        statusTopicReplications = containers.head.environments(WorkerCollie.STATUS_TOPIC_REPLICATIONS_KEY).toShort,
        // The JAR_INFOS_KEY does not exist if user doesn't pass any jar info in creating worker cluster
        jarInfos = containers.head.environments
          .get(WorkerCollie.JAR_INFOS_KEY)
          .map(WorkerCollie.toJarInfos)
          .getOrElse(Seq.empty),
        connectors = connectors,
        nodeNames = containers.map(_.nodeName).toSet,
        // Currently, docker and k8s has same naming rule for "Running",
        // it is ok that we use the containerState.RUNNING here.
        deadNodes = containers.filterNot(_.state == ContainerState.RUNNING.name).map(_.nodeName).toSet,
        // We do not care the user parameters since it's stored in configurator already
        tags = Map.empty,
        state = {
          // we only have two possible results here:
          // 1. only assume cluster is "running" if at least one container is running
          // 2. the cluster state is always "failed" if all containers were not running
          val alive = containers.exists(_.state == ClusterState.RUNNING.name)
          if (alive) Some(ContainerState.RUNNING.name) else Some(ClusterState.FAILED.name)
        },
        // TODO how could we fetch the error?...by Sam
        error = None,
        lastModified = CommonUtils.current()
      )
    }
  }

  /**
    * It tried to fetch connector information from starting worker cluster
    * However, it may be too slow to get latest connector information.
    * We don't throw exception since it is a common case, and Skipping retry can make quick response
    * @param connectionProps worker connection props
    * @return plugin description or nothing
    */
  private[this] def connectors(connectionProps: String)(
    implicit executionContext: ExecutionContext): Future[Seq[Definition]] =
    WorkerClient.builder.connectionProps(connectionProps).disableRetry().build.connectorDefinitions().recover {
      case e: Throwable =>
        ClusterCollie.LOG
          .error(s"Failed to fetch connectors information of cluster:$connectionProps. Use empty list instead", e)
        Seq.empty
    }

  private[this] def brokerContainers(bkClusterName: String)(
    implicit executionContext: ExecutionContext): Future[Seq[ContainerInfo]] = {
    brokerClusters.map(
      _.filter(_._1.isInstanceOf[BrokerClusterInfo])
        .find(_._1.name == bkClusterName)
        .map(_._2)
        .getOrElse(
          throw new NoSuchClusterException(s"broker cluster:$bkClusterName doesn't exist. other broker clusters: " +
            s"${brokerClusters.map(_.filter(_._1.isInstanceOf[BrokerClusterInfo]).map(_._1.name).mkString(","))}"))
    )
  }
}

object WorkerCollie {
  trait ClusterCreator extends Collie.ClusterCreator[WorkerClusterInfo] {
    private[this] var clientPort: Int = CommonUtils.availablePort()
    private[this] var brokerClusterName: String = _
    private[this] var groupId: String = CommonUtils.randomString(10)
    private[this] var offsetTopicName: String = s"$groupId-offset-${CommonUtils.randomString(10)}"
    private[this] var offsetTopicReplications: Short = 1
    private[this] var offsetTopicPartitions: Int = 1
    private[this] var configTopicName: String = s"$groupId-config-${CommonUtils.randomString(10)}"
    private[this] var configTopicReplications: Short = 1
    private[this] var statusTopicName: String = s"$groupId-status-${CommonUtils.randomString(10)}"
    private[this] var statusTopicReplications: Short = 1
    private[this] var statusTopicPartitions: Int = 1
    private[this] var jarInfos: Seq[FileInfo] = Seq.empty
    private[this] var jmxPort: Int = CommonUtils.availablePort()

    override protected def doCopy(clusterInfo: WorkerClusterInfo): Unit = {
      clientPort(clusterInfo.clientPort)
      brokerClusterName(clusterInfo.brokerClusterName)
      groupId(clusterInfo.groupId)
      offsetTopicName(clusterInfo.offsetTopicName)
      offsetTopicReplications(clusterInfo.offsetTopicReplications)
      offsetTopicPartitions(clusterInfo.offsetTopicPartitions)
      configTopicName(clusterInfo.configTopicName)
      configTopicReplications(clusterInfo.configTopicReplications)
      statusTopicName(clusterInfo.statusTopicName)
      statusTopicReplications(clusterInfo.statusTopicReplications)
      statusTopicPartitions(clusterInfo.statusTopicPartitions)
      jarInfos(clusterInfo.jarInfos)
      jmxPort(clusterInfo.jmxPort)
    }

    def brokerClusterName(name: String): ClusterCreator = {
      this.brokerClusterName = CommonUtils.requireNonEmpty(name)
      this
    }

    @Optional("default is random port")
    def clientPort(clientPort: Int): ClusterCreator = {
      this.clientPort = CommonUtils.requireConnectionPort(clientPort)
      this
    }

    @Optional("default is random string")
    def groupId(groupId: String): ClusterCreator = {
      this.groupId = CommonUtils.requireNonEmpty(groupId)
      this
    }

    @Optional("default is random string")
    def offsetTopicName(offsetTopicName: String): ClusterCreator = {
      this.offsetTopicName = CommonUtils.requireNonEmpty(offsetTopicName)
      this
    }

    @Optional("default number is 1")
    def offsetTopicReplications(offsetTopicReplications: Short): ClusterCreator = {
      this.offsetTopicReplications = CommonUtils.requirePositiveShort(offsetTopicReplications)
      this
    }
    @Optional("default number is 1")
    def offsetTopicPartitions(offsetTopicPartitions: Int): ClusterCreator = {
      this.offsetTopicPartitions = CommonUtils.requirePositiveInt(offsetTopicPartitions)
      this
    }

    @Optional("default is random string")
    def statusTopicName(statusTopicName: String): ClusterCreator = {
      this.statusTopicName = CommonUtils.requireNonEmpty(statusTopicName)
      this
    }

    @Optional("default number is 1")
    def statusTopicReplications(statusTopicReplications: Short): ClusterCreator = {
      this.statusTopicReplications = CommonUtils.requirePositiveShort(statusTopicReplications)
      this
    }
    @Optional("default number is 1")
    def statusTopicPartitions(statusTopicPartitions: Int): ClusterCreator = {
      this.statusTopicPartitions = CommonUtils.requireConnectionPort(statusTopicPartitions)
      this
    }

    @Optional("default is random string")
    def configTopicName(configTopicName: String): ClusterCreator = {
      this.configTopicName = CommonUtils.requireNonEmpty(configTopicName)
      this
    }

    @Optional("default number is 1")
    def configTopicReplications(configTopicReplications: Short): ClusterCreator = {
      this.configTopicReplications = CommonUtils.requirePositiveShort(configTopicReplications)
      this
    }

    @Optional("default is empty")
    def jarInfos(jarInfos: Seq[FileInfo]): ClusterCreator = {
      this.jarInfos = Objects.requireNonNull(jarInfos)
      this
    }

    @Optional("default is random port")
    def jmxPort(jmxPort: Int): ClusterCreator = {
      this.jmxPort = CommonUtils.requireConnectionPort(jmxPort)
      this
    }

    override def create(): Future[WorkerClusterInfo] = doCreate(
      executionContext = Objects.requireNonNull(executionContext),
      clusterName = CommonUtils.requireNonEmpty(clusterName),
      imageName = CommonUtils.requireNonEmpty(imageName),
      brokerClusterName = CommonUtils.requireNonEmpty(brokerClusterName),
      clientPort = CommonUtils.requireConnectionPort(clientPort),
      jmxPort = CommonUtils.requireConnectionPort(jmxPort),
      groupId = CommonUtils.requireNonEmpty(groupId),
      offsetTopicName = CommonUtils.requireNonEmpty(offsetTopicName),
      offsetTopicReplications = CommonUtils.requirePositiveShort(offsetTopicReplications),
      offsetTopicPartitions = CommonUtils.requirePositiveInt(offsetTopicPartitions),
      statusTopicName = CommonUtils.requireNonEmpty(statusTopicName),
      statusTopicReplications = CommonUtils.requirePositiveShort(statusTopicReplications),
      statusTopicPartitions = CommonUtils.requirePositiveInt(statusTopicPartitions),
      configTopicName = CommonUtils.requireNonEmpty(configTopicName),
      configTopicReplications = CommonUtils.requirePositiveShort(configTopicReplications),
      jarInfos = Objects.requireNonNull(jarInfos),
      nodeNames = CommonUtils.requireNonEmpty(nodeNames.asJava).asScala.toSet
    )

    override protected def checkClusterName(clusterName: String): String = {
      WorkerApi.WORKER_CREATION_JSON_FORMAT.check("name", JsString(clusterName))
      clusterName
    }

    protected def doCreate(executionContext: ExecutionContext,
                           clusterName: String,
                           imageName: String,
                           brokerClusterName: String,
                           clientPort: Int,
                           jmxPort: Int,
                           groupId: String,
                           offsetTopicName: String,
                           offsetTopicReplications: Short,
                           offsetTopicPartitions: Int,
                           statusTopicName: String,
                           statusTopicReplications: Short,
                           statusTopicPartitions: Int,
                           configTopicName: String,
                           configTopicReplications: Short,
                           jarInfos: Seq[FileInfo],
                           nodeNames: Set[String]): Future[WorkerClusterInfo]
  }
  private[agent] val GROUP_ID_KEY: String = "WORKER_GROUP"
  private[agent] val OFFSET_TOPIC_KEY: String = "WORKER_OFFSET_TOPIC"
  private[agent] val OFFSET_TOPIC_REPLICATIONS_KEY: String = "WORKER_OFFSET_TOPIC_REPLICATIONS"
  private[agent] val OFFSET_TOPIC_PARTITIONS_KEY: String = "WORKER_OFFSET_TOPIC_PARTITIONS"
  private[agent] val CONFIG_TOPIC_KEY: String = "WORKER_CONFIG_TOPIC"
  private[agent] val CONFIG_TOPIC_REPLICATIONS_KEY: String = "WORKER_CONFIG_TOPIC_REPLICATIONS"
  private[agent] val STATUS_TOPIC_KEY: String = "WORKER_STATUS_TOPIC"
  private[agent] val STATUS_TOPIC_REPLICATIONS_KEY: String = "WORKER_STATUS_TOPIC_REPLICATIONS"
  private[agent] val STATUS_TOPIC_PARTITIONS_KEY: String = "WORKER_STATUS_TOPIC_PARTITIONS"
  private[agent] val BROKERS_KEY: String = "WORKER_BROKERS"
  private[agent] val ADVERTISED_HOSTNAME_KEY: String = "WORKER_ADVERTISED_HOSTNAME"
  private[agent] val ADVERTISED_CLIENT_PORT_KEY: String = "WORKER_ADVERTISED_CLIENT_PORT"
  private[agent] val CLIENT_PORT_KEY: String = "WORKER_CLIENT_PORT"
  private[agent] val JAR_URLS_KEY: String = "WORKER_JAR_URLS"
  private[agent] val JAR_INFOS_KEY: String = "WORKER_JAR_INFOS"

  /**
    * internal key used to save the broker cluster name.
    * All nodes of worker cluster should have this environment variable.
    */
  private[agent] val BROKER_CLUSTER_NAME: String = "CCI_BROKER_CLUSTER_NAME"

  /**
    * this key has not been used yet
    */
  private[agent] val PLUGINS_KEY: String = "WORKER_PLUGINS"
  private[agent] val JMX_HOSTNAME_KEY: String = "JMX_HOSTNAME"
  private[agent] val JMX_PORT_KEY: String = "JMX_PORT"

  /**
    * We don't want to complicate our script used in starting worker node. For example, script has to parse the json string if we provide
    * a empty array via the env variable. . Hence, we just remove the keys from env if user does not specify them.
    * @param jarInfos jar information
    * @return a map with input value or empty if input is empty.
    */
  private[agent] def toMap(jarInfos: Seq[FileInfo]): Map[String, String] = if (jarInfos.isEmpty) Map.empty
  else
    Map(
      WorkerCollie.JAR_URLS_KEY -> jarInfos.map(_.url.toString).mkString(","),
      WorkerCollie.JAR_INFOS_KEY -> WorkerCollie.toString(jarInfos),
    )

  /**
    * convert the scala object to json string with backslash.
    * this string is written to linux env and the env does not accept the double quote. Hence, we add backslash with
    * double quotes to keep the origin form in env.
    */
  private[agent] def toString(jarInfos: Seq[FileInfo]): String =
    JsArray(jarInfos.map(FILE_INFO_JSON_FORMAT.write).toVector).toString.replaceAll("\"", "\\\\\"")

  import spray.json._

  /**
    * this method is in charge of converting string, which is serialized by toMap, to scala objects. We put those helper methods since we
    * have two kind collies that both of them requires those parser.
    * @param string json representation string with specific backslash and quote
    * @return jar information
    */
  private[agent] def toJarInfos(string: String): Seq[FileInfo] =
    // replace the backslash to nothing
    string.replaceAll("\\\\\"", "\"").parseJson.asInstanceOf[JsArray].elements.map(FILE_INFO_JSON_FORMAT.read)
}
