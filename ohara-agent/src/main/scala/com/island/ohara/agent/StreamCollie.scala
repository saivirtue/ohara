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

import java.net.URL
import java.util.Objects

import com.island.ohara.agent.docker.ContainerState
import com.island.ohara.client.configurator.v0.BrokerApi.BrokerClusterInfo
import com.island.ohara.client.configurator.v0.ContainerApi.{ContainerInfo, PortMapping, PortPair}
import com.island.ohara.client.configurator.v0.FileInfoApi.FileInfo
import com.island.ohara.client.configurator.v0.MetricsApi.Metrics
import com.island.ohara.client.configurator.v0.NodeApi.Node
import com.island.ohara.client.configurator.v0.StreamApi.{Creation, StreamClusterInfo}
import com.island.ohara.client.configurator.v0.{ClusterInfo, Definition, StreamApi}
import com.island.ohara.common.annotations.Optional
import com.island.ohara.common.setting.TopicKey
import com.island.ohara.common.util.CommonUtils
import com.island.ohara.metrics.BeanChannel
import com.island.ohara.metrics.basic.CounterMBean
import com.island.ohara.streams.config.StreamDefUtils
import com.typesafe.scalalogging.Logger
import spray.json._

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

/**
  * An interface of controlling stream cluster.
  * It isolates the implementation of container manager from Configurator.
  */
trait StreamCollie extends Collie[StreamClusterInfo] {
  private[this] val LOG = Logger(classOf[StreamCollie])

  override def creator: StreamCollie.ClusterCreator =
    (executionContext, creation) => {
      implicit val exec: ExecutionContext = executionContext
      clusters().flatMap(clusters => {
        if (clusters.keys.filter(_.isInstanceOf[StreamClusterInfo]).exists(_.name == creation.name))
          Future.failed(new IllegalArgumentException(s"stream cluster:${creation.name} exists!"))
        else {
          val jarInfo = creation.jarInfo.getOrElse(throw new RuntimeException("jarInfo should be defined"))
          nodeCollie
            .nodes(creation.nodeNames)
            .map(
              _.map(
                node => node -> Collie.format(prefixKey, creation.name, serviceName)
              ).toMap
            )
            // the broker cluster should be defined in data creating phase already
            // here we just throw an exception for absent value to ensure everything works as expect
            .flatMap(
              nodes =>
                brokerContainers(
                  creation.brokerClusterName.getOrElse(
                    throw new RuntimeException("broker cluser name should be defined")
                  )
                ).map(cs => (nodes, cs))
            )
            .flatMap {
              case (nodes, brokerContainers) =>
                val route = resolveHostNames(
                  (nodes.map(_._1.hostname)
                    ++ brokerContainers.map(_.nodeName)
                  // make sure the streamApp can connect to configurator
                    ++ Seq(jarInfo.url.getHost)).toSet
                )
                // ssh connection is slow so we submit request by multi-thread
                Future
                  .sequence(nodes.map {
                    case (node, containerName) =>
                      val containerInfo = ContainerInfo(
                        nodeName = node.name,
                        id = Collie.UNKNOWN,
                        imageName = creation.imageName,
                        created = Collie.UNKNOWN,
                        state = Collie.UNKNOWN,
                        kind = Collie.UNKNOWN,
                        name = containerName,
                        size = Collie.UNKNOWN,
                        portMappings = Seq(
                          PortMapping(
                            hostIp = Collie.UNKNOWN,
                            portPairs = Seq(
                              PortPair(
                                hostPort = creation.jmxPort,
                                containerPort = creation.jmxPort
                              )
                            )
                          )
                        ),
                        environments = creation.settings.map {
                          case (k, v) =>
                            k -> (v match {
                              // the string in json representation has quote in the beginning and end.
                              // we don't like the quotes since it obstruct us to cast value to pure string.
                              case JsString(s) => s
                              // save the json string for all settings
                              // StreamDefUtils offers the helper method to turn them back.
                              case _ => CommonUtils.toEnvString(v.toString)
                            })
                        }
                        // we convert all settings to specific string in order to fetch all settings from
                        // container env quickly. Also, the specific string enable us to pick up the "true" settings
                        // from envs since there are many system-defined settings in container envs.
                          + toEnvString(creation.settings),
                        // we should set the hostname to container name in order to avoid duplicate name with other containers
                        hostname = containerName
                      )
                      doCreator(executionContext, containerName, containerInfo, node, route, creation.jmxPort, jarInfo)
                        .map(_ => Some(containerInfo))
                  })
                  .map(_.flatten.toSeq)
                  .flatMap(
                    cs =>
                      loadDefinition(
                        creation.jarInfo
                          .getOrElse(
                            throw new RuntimeException("jarInfo should be defined")
                          )
                          .url
                      ).map(definition => cs -> definition)
                  )
                  .map {
                    case (successfulContainers, definition) =>
                      if (successfulContainers.isEmpty)
                        throw new IllegalArgumentException(
                          s"failed to create ${creation.name} on $serviceName"
                        )
                      val clusterInfo = StreamClusterInfo(
                        // the other arguments (clusterName, imageName and so on) are extracted from settings so
                        // we don't need to add them back to settings.
                        settings = StreamApi.access.request
                          .settings(creation.settings)
                          .nodeNames(successfulContainers.map(_.nodeName).toSet)
                          .creation
                          .settings,
                        // TODO: cluster info
                        definition = definition,
                        deadNodes = Set.empty,
                        metrics = Metrics.EMPTY,
                        // creating cluster success but still need to update state by another request
                        state = None,
                        error = None,
                        lastModified = CommonUtils.current()
                      )
                      postCreateCluster(clusterInfo, successfulContainers)
                      clusterInfo
                  }
            }
        }
      })
    }

  /**
    * Get all counter beans from cluster
    * @param cluster cluster
    * @return counter beans
    */
  def counters(cluster: StreamClusterInfo): Seq[CounterMBean] = cluster.aliveNodes.flatMap { node =>
    BeanChannel.builder().hostname(node).port(cluster.jmxPort).build().counterMBeans().asScala
  }.toSeq

  /**
    *
    * @return async future containing configs
    */
  /**
    * Get all '''SettingDef''' of current streamApp.
    * Note: This method intends to call a method that invokes the reflection method of streamApp.
    *
    * @param jarUrl the custom streamApp jar url
    * @return stream definition
    */
  //TODO : this workaround should be removed and use a new API instead in #2191...by Sam
  def loadDefinition(jarUrl: URL)(implicit executionContext: ExecutionContext): Future[Option[Definition]] =
    Future {
      import sys.process._
      val classpath = System.getProperty("java.class.path")
      val command =
        s"""java -cp "$classpath" ${StreamCollie.MAIN_ENTRY} ${StreamDefUtils.JAR_URL_DEFINITION
          .key()}=${jarUrl.toString} ${StreamCollie.CONFIG_KEY}"""
      val result = command.!!
      val className = result.split("=")(0)
      Some(Definition(className, StreamDefUtils.ofJson(result.split("=")(1)).values.asScala))
    }.recover {
      case e: RuntimeException =>
        // We cannot parse the provided jar, return nothing and log it
        LOG.warn(s"the provided jar url: [$jarUrl] could not be parsed, return default settings only.", e)
        None
    }

  private[agent] def toStreamCluster(clusterName: String, containers: Seq[ContainerInfo])(
    implicit executionContext: ExecutionContext): Future[StreamClusterInfo] = {
    // get the first running container, or first non-running container if not found
    val creation = StreamApi.access.request.settings(seekSettings(containers.head.environments)).creation
    loadDefinition(creation.jarInfo.get.url).map { definition =>
      StreamClusterInfo(
        settings = creation.settings,
        // we don't care the runtime definitions; it's saved to store already
        definition = definition,
        // Currently, docker and k8s has same naming rule for "Running",
        // it is ok that we use the containerState.RUNNING here.
        deadNodes = creation.nodeNames -- containers
          .filter(_.state == ContainerState.RUNNING.name)
          .map(_.nodeName)
          .toSet,
        metrics = Metrics.EMPTY,
        state = toClusterState(containers).map(_.name),
        error = None,
        lastModified = CommonUtils.current()
      )
    }
  }

  /**
    * Define nodeCollie by different environment
    * @return
    */
  protected def nodeCollie: NodeCollie

  /**
    * Define prefixKey by different environment
    * @return prefix key
    */
  protected def prefixKey: String

  override val serviceName: String = StreamApi.STREAM_SERVICE_NAME

  protected def doCreator(executionContext: ExecutionContext,
                          containerName: String,
                          containerInfo: ContainerInfo,
                          node: Node,
                          route: Map[String, String],
                          jmxPort: Int,
                          jarInfo: FileInfo): Future[Unit]

  protected def postCreateCluster(clusterInfo: ClusterInfo, successfulContainers: Seq[ContainerInfo]): Unit = {
    //Default do nothing
  }

  /**
    * get the containers for specific broker cluster. This method is used to update the route.
    * @param clusterName name of broker cluster
    * @param executionContext thread pool
    * @return containers
    */
  protected def brokerContainers(clusterName: String)(
    implicit executionContext: ExecutionContext): Future[Seq[ContainerInfo]]
}

object StreamCollie {
  trait ClusterCreator extends Collie.ClusterCreator[StreamClusterInfo] {
    private[this] val request = StreamApi.access.request
    override protected def doCopy(clusterInfo: StreamClusterInfo): Unit = request.settings(clusterInfo.settings)

    /**
      * set the jar url for the streamApp running
      *
      * @param jarInfo jar info
      * @return this creator
      */
    def jarInfo(jarInfo: FileInfo): ClusterCreator = {
      request.jarInfo(jarInfo)
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

    /**
      * add the key-value data map for container
      *
      * @param settings data settings
      * @return this creator
      */
    @Optional("default is empty map")
    def settings(settings: Map[String, JsValue]): ClusterCreator = {
      request.settings(settings)
      this
    }

    def brokerCluster(cluster: BrokerClusterInfo): ClusterCreator = {
      brokerClusterName(cluster.name)
      connectionProps(cluster.connectionProps)
    }

    def brokerClusterName(brokerClusterName: String): ClusterCreator = {
      request.brokerClusterName(brokerClusterName)
      this
    }

    def connectionProps(connectionProps: String): ClusterCreator = {
      request.connectionProps(connectionProps)
      this
    }

    def jmxPort(jmxPort: Int): ClusterCreator = {
      request.jmxPort(jmxPort)
      this
    }

    def fromTopicKey(fromTopicKey: TopicKey): ClusterCreator = fromTopicKeys(Set(fromTopicKey))

    def fromTopicKeys(fromTopicKeys: Set[TopicKey]): ClusterCreator = {
      request.fromTopicKeys(fromTopicKeys)
      this
    }

    def toTopicKey(toTopicKey: TopicKey): ClusterCreator = toTopicKeys(Set(toTopicKey))

    def toTopicKeys(toTopicKeys: Set[TopicKey]): ClusterCreator = {
      request.toTopicKeys(toTopicKeys)
      this
    }

    override def create(): Future[StreamClusterInfo] = {
      // initial the basic creation required parameters (defined in ClusterInfo) for stream
      val creation = request.name(clusterName).imageName(imageName).nodeNames(nodeNames).creation

      // TODO: the to/from topics should not be empty in building creation ... However, our stream route
      // allowed user to enter empty for both fields... With a view to keeping the compatibility
      // we have to move the check from "parsing json" to "running cluster"
      // I'd say it is inconsistent to our cluster route ... by chia
      CommonUtils.requireNonEmpty(creation.from.asJava)
      CommonUtils.requireNonEmpty(creation.to.asJava)
      doCreate(
        executionContext = Objects.requireNonNull(executionContext),
        creation = creation
      )
    }

    override protected def checkClusterName(clusterName: String): String = {
      StreamApi.STREAM_CREATION_JSON_FORMAT.check("name", JsString(clusterName))
      clusterName
    }

    protected def doCreate(executionContext: ExecutionContext, creation: Creation): Future[StreamClusterInfo]
  }

  /**
    * the only entry for ohara streamApp
    */
  val MAIN_ENTRY = "com.island.ohara.streams.StreamApp"

  /**
    * the flag to get/set streamApp configs for container
    */
  private[agent] val CONFIG_KEY = "CONFIG_KEY"

  /**
    * generate the jmx required properties
    *
    * @param hostname the hostname used by jmx remote
    * @param port the port used by jmx remote
    * @return jmx properties
    */
  private[agent] def formatJMXProperties(hostname: String, port: Int): Seq[String] = {
    Seq(
      "-Dcom.sun.management.jmxremote",
      "-Dcom.sun.management.jmxremote.authenticate=false",
      "-Dcom.sun.management.jmxremote.ssl=false",
      s"-Dcom.sun.management.jmxremote.port=$port",
      s"-Dcom.sun.management.jmxremote.rmi.port=$port",
      s"-Djava.rmi.server.hostname=$hostname"
    )
  }
}
