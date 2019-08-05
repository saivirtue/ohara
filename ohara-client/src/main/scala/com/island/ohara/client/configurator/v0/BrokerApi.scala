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

package com.island.ohara.client.configurator.v0

import java.util.Objects

import com.island.ohara.common.annotations.Optional
import com.island.ohara.common.setting.SettingDef
import com.island.ohara.common.util.{CommonUtils, VersionUtils}
import spray.json.DefaultJsonProtocol._
import spray.json.{JsArray, JsString, JsValue}

import scala.concurrent.{ExecutionContext, Future}

object BrokerApi {

  /**
    * The default value of group for this API.
    */
  val GROUP_DEFAULT: String = com.island.ohara.client.configurator.v0.GROUP_DEFAULT
  val LIMIT_OF_NAME_LENGTH: Int = ZookeeperApi.LIMIT_OF_NAME_LENGTH

  val BROKER_PREFIX_PATH: String = "brokers"

  val BROKER_SERVICE_NAME: String = "bk"

  /**
    * the default docker image used to run containers of broker cluster
    */
  val IMAGE_NAME_DEFAULT: String = s"oharastream/broker:${VersionUtils.VERSION}"

  final case class Creation private[BrokerApi] (name: String,
                                                imageName: String,
                                                zookeeperClusterName: Option[String],
                                                exporterPort: Int,
                                                clientPort: Int,
                                                jmxPort: Int,
                                                nodeNames: Set[String],
                                                tags: Map[String, JsValue])
      extends ClusterCreationRequest {
    override def group: String = GROUP_DEFAULT
    override def ports: Set[Int] = Set(clientPort, exporterPort, jmxPort)
  }

  /**
    * exposed to configurator
    */
  private[ohara] implicit val BROKER_CREATION_JSON_FORMAT: OharaJsonFormat[Creation] =
    ClusterJsonRefiner
      .basicRulesOfCreation[Creation](IMAGE_NAME_DEFAULT)
      .format(jsonFormat8(Creation))
      .nullToRandomPort("clientPort")
      .requireBindPort("clientPort")
      .nullToRandomPort("exporterPort")
      .requireBindPort("exporterPort")
      .nullToRandomPort("jmxPort")
      .requireBindPort("jmxPort")
      .refine

  final case class Update private[BrokerApi] (imageName: Option[String],
                                              zookeeperClusterName: Option[String],
                                              exporterPort: Option[Int],
                                              clientPort: Option[Int],
                                              jmxPort: Option[Int],
                                              nodeNames: Option[Set[String]],
                                              tags: Option[Map[String, JsValue]])
      extends ClusterUpdateRequest
  implicit val BROKER_UPDATE_JSON_FORMAT: OharaJsonFormat[Update] =
    ClusterJsonRefiner
      .basicRulesOfUpdate[Update]
      .format(jsonFormat7(Update))
      .requireBindPort("clientPort")
      .requireBindPort("exporterPort")
      .requireBindPort("jmxPort")
      .refine

  final case class BrokerClusterInfo private[BrokerApi] (name: String,
                                                         imageName: String,
                                                         zookeeperClusterName: String,
                                                         clientPort: Int,
                                                         exporterPort: Int,
                                                         jmxPort: Int,
                                                         nodeNames: Set[String],
                                                         deadNodes: Set[String],
                                                         tags: Map[String, JsValue],
                                                         lastModified: Long,
                                                         state: Option[String],
                                                         error: Option[String],
                                                         topicSettingDefinitions: Seq[SettingDef])
      extends ClusterInfo {
    // cluster does not support to define group
    override def group: String = GROUP_DEFAULT
    override def kind: String = BROKER_SERVICE_NAME
    override def ports: Set[Int] = Set(clientPort, exporterPort, jmxPort)
    override def clone(newNodeNames: Set[String]): ClusterInfo = copy(nodeNames = newNodeNames)
    def connectionProps: String = nodeNames.map(n => s"$n:$clientPort").mkString(",")
  }

  /**
    * exposed to configurator
    */
  private[ohara] implicit val BROKER_CLUSTER_INFO_JSON_FORMAT: OharaJsonFormat[BrokerClusterInfo] =
    JsonRefiner[BrokerClusterInfo].format(jsonFormat13(BrokerClusterInfo)).refine

  /**
    * used to generate the payload and url for POST/PUT request.
    */
  sealed trait Request {
    @Optional("default name is a random string")
    def name(name: String): Request
    @Optional("the default image is IMAGE_NAME_DEFAULT")
    def imageName(imageName: String): Request
    @Optional("Ignoring zookeeper cluster name enable server to match a zk for you")
    def zookeeperClusterName(zookeeperClusterName: String): Request
    @Optional("the default port is random")
    def clientPort(clientPort: Int): Request
    @Optional("the default port is random")
    def exporterPort(exporterPort: Int): Request
    @Optional("the default port is random")
    def jmxPort(jmxPort: Int): Request
    def nodeName(nodeName: String): Request = nodeNames(Set(CommonUtils.requireNonEmpty(nodeName)))
    def nodeNames(nodeNames: Set[String]): Request
    @Optional("default value is empty array in creation and None in update")
    def tags(tags: Map[String, JsValue]): Request

    /**
      * generate the POST request
      * @param executionContext thread pool
      * @return created data
      */
    def create()(implicit executionContext: ExecutionContext): Future[BrokerClusterInfo]

    /**
      * generate the PUT request
      * @param executionContext execution context
      * @return updated/created data
      */
    def update()(implicit executionContext: ExecutionContext): Future[BrokerClusterInfo]

    /**
      * for testing only
      * @return the payload of creation
      */
    private[v0] def creation: Creation

    /**
      * for testing only
      * @return the payload of update
      */
    private[v0] def update: Update
  }

  final class Access private[BrokerApi] extends ClusterAccess[BrokerClusterInfo](BROKER_PREFIX_PATH, GROUP_DEFAULT) {
    def request: Request = new Request {
      private[this] var name: String = CommonUtils.randomString(LIMIT_OF_NAME_LENGTH)
      private[this] var imageName: Option[String] = None
      private[this] var zookeeperClusterName: Option[String] = None
      private[this] var clientPort: Option[Int] = None
      private[this] var exporterPort: Option[Int] = None
      private[this] var jmxPort: Option[Int] = None
      private[this] var nodeNames: Option[Set[String]] = None
      private[this] var tags: Map[String, JsValue] = _
      override def name(name: String): Request = {
        this.name = CommonUtils.requireNonEmpty(name)
        this
      }

      override def imageName(imageName: String): Request = {
        this.imageName = Some(CommonUtils.requireNonEmpty(imageName))
        this
      }

      override def zookeeperClusterName(zookeeperClusterName: String): Request = {
        this.zookeeperClusterName = Some(CommonUtils.requireNonEmpty(zookeeperClusterName))
        this
      }

      override def clientPort(clientPort: Int): Request = {
        this.clientPort = Some(CommonUtils.requireConnectionPort(clientPort))
        this
      }

      override def exporterPort(exporterPort: Int): Request = {
        this.exporterPort = Some(CommonUtils.requireConnectionPort(exporterPort))
        this
      }

      override def jmxPort(jmxPort: Int): Request = {
        this.jmxPort = Some(CommonUtils.requireConnectionPort(jmxPort))
        this
      }

      import scala.collection.JavaConverters._
      override def nodeNames(nodeNames: Set[String]): Request = {
        this.nodeNames = Some(CommonUtils.requireNonEmpty(nodeNames.asJava).asScala.toSet)
        BROKER_CREATION_JSON_FORMAT.check("nodeNames", JsArray(nodeNames.map(JsString(_)).toVector))
        this
      }

      override def tags(tags: Map[String, JsValue]): Request = {
        this.tags = Objects.requireNonNull(tags)
        this
      }

      override private[v0] def creation = Creation(
        name = CommonUtils.requireNonEmpty(name),
        imageName = CommonUtils.requireNonEmpty(imageName.getOrElse(IMAGE_NAME_DEFAULT)),
        zookeeperClusterName = zookeeperClusterName.map(CommonUtils.requireNonEmpty),
        clientPort = CommonUtils.requireConnectionPort(clientPort.getOrElse(CommonUtils.availablePort())),
        exporterPort = CommonUtils.requireConnectionPort(exporterPort.getOrElse(CommonUtils.availablePort())),
        jmxPort = CommonUtils.requireConnectionPort(jmxPort.getOrElse(CommonUtils.availablePort())),
        nodeNames = CommonUtils.requireNonEmpty(nodeNames.getOrElse(Set.empty).asJava).asScala.toSet,
        tags = if (tags == null) Map.empty else tags
      )

      override private[v0] def update: Update = Update(
        imageName = imageName.map(CommonUtils.requireNonEmpty),
        zookeeperClusterName = zookeeperClusterName.map(CommonUtils.requireNonEmpty),
        clientPort = clientPort.map(CommonUtils.requireConnectionPort),
        exporterPort = exporterPort.map(CommonUtils.requireConnectionPort),
        jmxPort = jmxPort.map(CommonUtils.requireConnectionPort),
        nodeNames = nodeNames.map(seq => CommonUtils.requireNonEmpty(seq.asJava).asScala.toSet),
        tags = Option(tags)
      )

      override def create()(implicit executionContext: ExecutionContext): Future[BrokerClusterInfo] =
        exec.post[Creation, BrokerClusterInfo, ErrorApi.Error](
          url,
          creation
        )

      override def update()(implicit executionContext: ExecutionContext): Future[BrokerClusterInfo] =
        exec.put[Update, BrokerClusterInfo, ErrorApi.Error](
          s"$url/${CommonUtils.requireNonEmpty(name)}",
          update
        )
    }
  }

  def access: Access = new Access
}
