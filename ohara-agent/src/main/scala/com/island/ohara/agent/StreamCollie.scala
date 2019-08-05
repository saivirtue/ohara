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

import java.net.{URI, URL}
import java.util.Objects

import com.island.ohara.agent.docker.ContainerState
import com.island.ohara.client.configurator.v0.ContainerApi.{ContainerInfo, PortMapping, PortPair}
import com.island.ohara.client.configurator.v0.MetricsApi.Metrics
import com.island.ohara.client.configurator.v0.NodeApi.Node
import com.island.ohara.client.configurator.v0.StreamApi.StreamClusterInfo
import com.island.ohara.client.configurator.v0.{ClusterInfo, Definition, StreamApi}
import com.island.ohara.common.annotations.Optional
import com.island.ohara.common.setting.ObjectKey
import com.island.ohara.common.util.CommonUtils
import com.island.ohara.metrics.BeanChannel
import com.island.ohara.metrics.basic.CounterMBean
import com.island.ohara.streams.config.StreamDefinitions
import com.island.ohara.streams.config.StreamDefinitions.DefaultConfigs
import com.typesafe.scalalogging.Logger
import spray.json.{JsArray, JsNumber, JsObject, JsString}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

/**
  * An interface of controlling stream cluster.
  * It isolates the implementation of container manager from Configurator.
  */
trait StreamCollie extends Collie[StreamClusterInfo, StreamCollie.ClusterCreator] {
  private[this] val LOG = Logger(classOf[StreamCollie])

  override def creator: StreamCollie.ClusterCreator =
    (clusterName, nodeNames, imageName, jarUrl, jmxPort, settings, executionContext) => {
      implicit val exec: ExecutionContext = executionContext
      clusters().flatMap(clusters => {
        if (clusters.keys.filter(_.isInstanceOf[StreamClusterInfo]).exists(_.name == clusterName))
          Future.failed(new IllegalArgumentException(s"stream cluster:$clusterName exists!"))
        else
          nodeCollie
            .nodes(nodeNames)
            .map(_.map(node => node -> ContainerCollie.format(prefixKey, clusterName, serviceName)).toMap)
            .flatMap {
              nodes =>
                def urlToHost(url: String): String = new URI(url).getHost

                val route: Map[String, String] = nodes.keys.map { node =>
                  node.hostname -> CommonUtils.address(node.hostname)
                }.toMap +
                  // make sure the streamApp can connect to configurator
                  (urlToHost(jarUrl) -> CommonUtils.address(urlToHost(jarUrl)))
                // ssh connection is slow so we submit request by multi-thread
                Future
                  .sequence(nodes.map {
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
                            portPairs = Seq(
                              PortPair(
                                hostPort = jmxPort,
                                containerPort = jmxPort
                              )
                            )
                          )
                        ),
                        environments = settings,
                        // we should set the hostname to container name in order to avoid duplicate name with other containers
                        hostname = containerName
                      )
                      doCreator(executionContext, containerName, containerInfo, node, route, jmxPort, jarUrl).map(_ =>
                        Some(containerInfo))
                  })
                  .map(_.flatten.toSeq)
                  .map {
                    successfulContainers =>
                      if (successfulContainers.isEmpty)
                        throw new IllegalArgumentException(s"failed to create $clusterName on $serviceName")
                      val jarKey = StreamCollie.urlToDataKey(jarUrl)
                      val clusterInfo = StreamClusterInfo(
                        settings = Map(
                          DefaultConfigs.NAME_DEFINITION.key() -> JsString(clusterName),
                          DefaultConfigs.IMAGE_NAME_DEFINITION.key() -> JsString(imageName),
                          DefaultConfigs.INSTANCES_DEFINITION.key() -> JsNumber(successfulContainers.size),
                          DefaultConfigs.JAR_KEY_DEFINITION.key() -> JsString(ObjectKey.toJsonString(jarKey)),
                          DefaultConfigs.FROM_TOPICS_DEFINITION.key() -> JsArray(
                            JsString(settings(DefaultConfigs.FROM_TOPICS_DEFINITION.key()))
                          ),
                          DefaultConfigs.TO_TOPICS_DEFINITION.key() -> JsArray(
                            JsString(settings(DefaultConfigs.TO_TOPICS_DEFINITION.key()))
                          ),
                          DefaultConfigs.JMX_PORT_DEFINITION.key() -> JsNumber(jmxPort),
                        ),
                        // we don't care the runtime definitions; it's saved to store already
                        definition = None,
                        nodeNames = successfulContainers.map(_.nodeName).toSet,
                        deadNodes = Set.empty,
                        metrics = Metrics(Seq.empty),
                        // creating cluster success but still need to update state by another request
                        state = None,
                        error = None,
                        lastModified = CommonUtils.current()
                      )
                      postCreateCluster(clusterInfo, successfulContainers)
                      clusterInfo
                  }
            }
      })
    }

  /**
    * Get all counter beans from cluster
    * @param cluster cluster
    * @return counter beans
    */
  def counters(cluster: StreamClusterInfo): Seq[CounterMBean] = cluster.nodeNames.flatMap { node =>
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
  def definitions(jarUrl: URL): Future[Option[Definition]] =
    Future.successful {
      import sys.process._
      val classpath = System.getProperty("java.class.path")
      val command =
        s"""java -cp $classpath ${StreamCollie.MAIN_ENTRY} ${DefaultConfigs.JAR_KEY_DEFINITION
          .key()}=\"${jarUrl.toString}\" ${StreamCollie.CONFIG_KEY}"""
      try {
        val result = command.!!
        Some(Definition(result.split("=")(0), StreamDefinitions.ofJson(result.split("=")(1)).values().asScala))
      } catch {
        case e: RuntimeException =>
          // We cannot parse the provided jar, return nothing and log it
          LOG.warn(s"the provided jar url: [$jarUrl] could not be parsed, skipped it :", e)
          None
      }
    }

  private[agent] def toStreamCluster(clusterName: String, containers: Seq[ContainerInfo]): Future[StreamClusterInfo] = {
    // get the first running container, or first non-running container if not found
    val first = containers.find(_.state == ContainerState.RUNNING.name).getOrElse(containers.head)
    val jarKey = ObjectKey.toObjectKey(first.environments(DefaultConfigs.JAR_KEY_DEFINITION.key()))
    Future.successful(
      StreamClusterInfo(
        settings = Map(
          DefaultConfigs.NAME_DEFINITION.key() -> JsString(clusterName),
          DefaultConfigs.IMAGE_NAME_DEFINITION.key() -> JsString(first.imageName),
          DefaultConfigs.INSTANCES_DEFINITION.key() -> JsNumber(containers.size),
          DefaultConfigs.JAR_KEY_DEFINITION.key() -> JsObject(
            com.island.ohara.client.configurator.v0.GROUP_KEY -> JsString(jarKey.group()),
            com.island.ohara.client.configurator.v0.NAME_KEY -> JsString(jarKey.name())),
          DefaultConfigs.FROM_TOPICS_DEFINITION.key() -> JsArray(
            JsString(first.environments(DefaultConfigs.FROM_TOPICS_DEFINITION.key()))
          ),
          DefaultConfigs.TO_TOPICS_DEFINITION.key() -> JsArray(
            JsString(first.environments(DefaultConfigs.TO_TOPICS_DEFINITION.key()))
          ),
          DefaultConfigs.JMX_PORT_DEFINITION.key() -> JsNumber(
            first.environments(DefaultConfigs.JMX_PORT_DEFINITION.key()).toInt)
        ),
        // we don't care the runtime definitions; it's saved to store already
        definition = None,
        nodeNames = containers.map(_.nodeName).toSet,
        // Currently, docker and k8s has same naming rule for "Running",
        // it is ok that we use the containerState.RUNNING here.
        deadNodes = containers.filterNot(_.state == ContainerState.RUNNING.name).map(_.nodeName).toSet,
        metrics = Metrics(Seq.empty),
        state = {
          // we only have two possible results here:
          // 1. only assume cluster is "running" if at least one container is running
          // 2. the cluster state is always "dead" if all containers were not running
          val alive = containers.exists(_.state == ContainerState.RUNNING.name)
          if (alive) Some(ClusterState.RUNNING.name) else Some(ClusterState.FAILED.name)
        },
        error = None,
        lastModified = CommonUtils.current()
      )
    )
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

  /**
    * Define serviceName by different environment
    * @return service name
    */
  protected def serviceName: String

  protected def doCreator(executionContext: ExecutionContext,
                          containerName: String,
                          containerInfo: ContainerInfo,
                          node: Node,
                          route: Map[String, String],
                          jmxPort: Int,
                          jarUrl: String): Future[Unit]

  protected def postCreateCluster(clusterInfo: ClusterInfo, successfulContainers: Seq[ContainerInfo]): Unit = {
    //Default do nothing
  }
}

object StreamCollie {
  trait ClusterCreator extends Collie.ClusterCreator[StreamClusterInfo] {
    private[this] var jarUrl: String = _
    private[this] var jmxPort: Int = CommonUtils.availablePort()
    private[this] var settings: Map[String, String] = Map.empty

    override protected def doCopy(clusterInfo: StreamClusterInfo): Unit = {
      // doCopy is used to add node for a running cluster.
      // Currently, StreamClusterInfo does not carry enough information to be copied and it is unsupported to add a node to a running streamapp cluster
      // Hence, it is fine to do nothing here
      // TODO: fill the correct implementation if we support to add node to a running streamapp cluster.
    }

    /**
      * set the jar url for the streamApp running
      *
      * @param jarUrl jar url
      * @return this creator
      */
    def jarUrl(jarUrl: String): ClusterCreator = {
      this.jarUrl = CommonUtils.requireNonEmpty(jarUrl)
      this
    }

    /**
      * set the jmx port
      *
      * @param jmxPort jmx port
      * @return this creator
      */
    @Optional("default is local random port")
    def jmxPort(jmxPort: Int): ClusterCreator = {
      this.jmxPort = CommonUtils.requireConnectionPort(jmxPort)
      this
    }

    /**
      * set the key-value data map for container
      *
      * @param settings data settings
      * @return this creator
      */
    @Optional("default is empty map")
    def settings(settings: Map[String, String]): ClusterCreator = {
      this.settings = Objects.requireNonNull(settings)
      this
    }

    override def create(): Future[StreamClusterInfo] = doCreate(
      CommonUtils.requireNonEmpty(clusterName),
      CommonUtils.requireNonEmpty(nodeNames.asJava).asScala.toSet,
      CommonUtils.requireNonEmpty(imageName),
      CommonUtils.requireNonEmpty(jarUrl),
      CommonUtils.requireConnectionPort(jmxPort),
      Objects.requireNonNull(settings),
      Objects.requireNonNull(executionContext)
    )

    override protected def checkClusterName(clusterName: String): String = {
      StreamApi.STREAM_CREATION_JSON_FORMAT.check("name", JsString(clusterName))
      clusterName
    }

    protected def doCreate(clusterName: String,
                           nodeNames: Set[String],
                           imageName: String,
                           jarUrl: String,
                           jmxPort: Int,
                           settings: Map[String, String],
                           executionContext: ExecutionContext): Future[StreamClusterInfo]
  }

  /**
    * the only entry for ohara streamApp
    */
  private[agent] val MAIN_ENTRY = "com.island.ohara.streams.StreamApp"

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

  /**
    * This is a helper method to convert the jar url to DataKey
    *
    * @param jarUrl jar url
    * @return data key
    */
  private[agent] def urlToDataKey(jarUrl: String): ObjectKey = {
    val name = jarUrl.split("\\/").last
    val group = jarUrl.split("\\/").init.last
    ObjectKey.of(group, name)
  }
}
