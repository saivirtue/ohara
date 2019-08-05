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

import com.island.ohara.client.configurator.v0.FileInfoApi._
import com.island.ohara.common.annotations.Optional
import com.island.ohara.common.setting.ObjectKey
import com.island.ohara.common.util.{CommonUtils, VersionUtils}
import spray.json.DefaultJsonProtocol._
import spray.json.{JsArray, JsNull, JsNumber, JsObject, JsString, JsValue, RootJsonFormat}

import scala.concurrent.{ExecutionContext, Future}
object WorkerApi {

  /**
    * The default value of group for this API.
    */
  val GROUP_DEFAULT: String = com.island.ohara.client.configurator.v0.GROUP_DEFAULT
  val WORKER_SERVICE_NAME: String = "wk"

  val LIMIT_OF_NAME_LENGTH: Int = ZookeeperApi.LIMIT_OF_NAME_LENGTH

  val WORKER_PREFIX_PATH: String = "workers"

  /**
    * the default docker image used to run containers of worker cluster
    */
  val IMAGE_NAME_DEFAULT: String = s"oharastream/connect-worker:${VersionUtils.VERSION}"

  private[this] val IMAGE_NAME_KEY = "imageName"
  private[this] val BROKER_CLUSTER_NAME_KEY = "brokerClusterName"
  private[this] val CLIENT_PORT_KEY = "clientPort"
  private[this] val JMX_PORT_KEY = "jmxPort"
  private[this] val GROUP_ID_KEY = "groupId"
  private[this] val STATUS_TOPIC_NAME_KEY = "statusTopicName"
  private[this] val STATUS_TOPIC_PARTITIONS_KEY = "statusTopicPartitions"
  private[this] val STATUS_TOPIC_REPLICATIONS_KEY = "statusTopicReplications"
  private[this] val CONFIG_TOPIC_NAME_KEY = "configTopicName"
  private[this] val CONFIG_TOPIC_PARTITIONS_KEY = "configTopicPartitions"
  private[this] val CONFIG_TOPIC_REPLICATIONS_KEY = "configTopicReplications"
  private[this] val OFFSET_TOPIC_NAME_KEY = "offsetTopicName"
  private[this] val OFFSET_TOPIC_PARTITIONS_KEY = "offsetTopicPartitions"
  private[this] val OFFSET_TOPIC_REPLICATIONS_KEY = "offsetTopicReplications"
  private[this] val JAR_INFOS_KEY = "jarInfos"
  private[this] val CONNECTORS_KEY = "connectors"
  private[this] val NODE_NAMES_KEY = "nodeNames"
  private[this] val DEAD_NODES_KEY = "deadNodes"
  private[this] val TAGS_KEY = "tags"
  private[this] val LASTMODIFIED_KEY = "lastModified"
  private[this] val STATE_KEY = "state"
  private[this] val ERROR_KEY = "error"

  final case class Creation private[WorkerApi] (name: String,
                                                imageName: String,
                                                brokerClusterName: Option[String],
                                                clientPort: Int,
                                                jmxPort: Int,
                                                groupId: String,
                                                configTopicName: String,
                                                // configTopicPartitions must be 1
                                                configTopicReplications: Short,
                                                offsetTopicName: String,
                                                offsetTopicPartitions: Int,
                                                offsetTopicReplications: Short,
                                                statusTopicName: String,
                                                statusTopicPartitions: Int,
                                                statusTopicReplications: Short,
                                                jarKeys: Set[ObjectKey],
                                                nodeNames: Set[String],
                                                tags: Map[String, JsValue])
      extends ClusterCreationRequest {
    override def group: String = GROUP_DEFAULT
    override def ports: Set[Int] = Set(clientPort, jmxPort)
  }

  /**
    * exposed to configurator
    */
  private[ohara] implicit val WORKER_CREATION_JSON_FORMAT: OharaJsonFormat[Creation] =
    ClusterJsonRefiner
      .basicRulesOfCreation[Creation](IMAGE_NAME_DEFAULT)
      .format(jsonFormat17(Creation))
      .rejectNegativeNumber()
      .nullToRandomPort("clientPort")
      .requireBindPort("clientPort")
      .nullToRandomPort("jmxPort")
      .requireBindPort("jmxPort")
      .nullToRandomString("groupId")
      .nullToRandomString("configTopicName")
      .nullToShort("configTopicReplications", 1)
      .nullToRandomString("offsetTopicName")
      .nullToInt("offsetTopicPartitions", 1)
      .nullToShort("offsetTopicReplications", 1)
      .nullToRandomString("statusTopicName")
      .nullToInt("statusTopicPartitions", 1)
      .nullToShort("statusTopicReplications", 1)
      .nullToEmptyArray("jarKeys")
      .refine

  final case class Update private[WorkerApi] (imageName: Option[String],
                                              brokerClusterName: Option[String],
                                              clientPort: Option[Int],
                                              jmxPort: Option[Int],
                                              groupId: Option[String],
                                              configTopicName: Option[String],
                                              // configTopicPartitions must be 1
                                              configTopicReplications: Option[Short],
                                              offsetTopicName: Option[String],
                                              offsetTopicPartitions: Option[Int],
                                              offsetTopicReplications: Option[Short],
                                              statusTopicName: Option[String],
                                              statusTopicPartitions: Option[Int],
                                              statusTopicReplications: Option[Short],
                                              jarKeys: Option[Set[ObjectKey]],
                                              nodeNames: Option[Set[String]],
                                              tags: Option[Map[String, JsValue]])
      extends ClusterUpdateRequest
  implicit val WORKER_UPDATE_JSON_FORMAT: OharaJsonFormat[Update] =
    ClusterJsonRefiner
      .basicRulesOfUpdate[Update]
      .format(jsonFormat16(Update))
      .rejectNegativeNumber()
      .requireBindPort("clientPort")
      .requireBindPort("jmxPort")
      .refine

  final case class WorkerClusterInfo private[ohara] (name: String,
                                                     imageName: String,
                                                     brokerClusterName: String,
                                                     clientPort: Int,
                                                     jmxPort: Int,
                                                     groupId: String,
                                                     statusTopicName: String,
                                                     statusTopicPartitions: Int,
                                                     statusTopicReplications: Short,
                                                     configTopicName: String,
                                                     configTopicPartitions: Int,
                                                     configTopicReplications: Short,
                                                     offsetTopicName: String,
                                                     offsetTopicPartitions: Int,
                                                     offsetTopicReplications: Short,
                                                     jarInfos: Seq[FileInfo],
                                                     connectors: Seq[Definition],
                                                     nodeNames: Set[String],
                                                     deadNodes: Set[String],
                                                     tags: Map[String, JsValue],
                                                     lastModified: Long,
                                                     state: Option[String],
                                                     error: Option[String])
      extends ClusterInfo {

    /**
      * Our client to broker and worker accept the connection props:host:port,host2:port2
      */
    def connectionProps: String = nodeNames.map(n => s"$n:$clientPort").mkString(",")

    override def ports: Set[Int] = Set(clientPort, jmxPort)

    override def group: String = GROUP_DEFAULT

    override def kind: String = WORKER_SERVICE_NAME

    override def clone(newNodeNames: Set[String]): ClusterInfo = copy(nodeNames = newNodeNames)
  }

  /**
    * exposed to configurator
    */
  private[ohara] implicit val WORKER_CLUSTER_INFO_JSON_FORMAT: OharaJsonFormat[WorkerClusterInfo] =
    JsonRefiner[WorkerClusterInfo]
      .format(new RootJsonFormat[WorkerClusterInfo] {
        override def write(obj: WorkerClusterInfo): JsValue = JsObject(
          noJsNull(
            Map(
              NAME_KEY -> JsString(obj.name),
              IMAGE_NAME_KEY -> JsString(obj.imageName),
              BROKER_CLUSTER_NAME_KEY -> JsString(obj.brokerClusterName),
              CLIENT_PORT_KEY -> JsNumber(obj.clientPort),
              JMX_PORT_KEY -> JsNumber(obj.jmxPort),
              GROUP_ID_KEY -> JsString(obj.groupId),
              STATUS_TOPIC_NAME_KEY -> JsString(obj.statusTopicName),
              STATUS_TOPIC_PARTITIONS_KEY -> JsNumber(obj.statusTopicPartitions),
              STATUS_TOPIC_REPLICATIONS_KEY -> JsNumber(obj.statusTopicReplications),
              CONFIG_TOPIC_NAME_KEY -> JsString(obj.configTopicName),
              CONFIG_TOPIC_PARTITIONS_KEY -> JsNumber(obj.configTopicPartitions),
              CONFIG_TOPIC_REPLICATIONS_KEY -> JsNumber(obj.configTopicReplications),
              OFFSET_TOPIC_NAME_KEY -> JsString(obj.offsetTopicName),
              OFFSET_TOPIC_PARTITIONS_KEY -> JsNumber(obj.offsetTopicPartitions),
              OFFSET_TOPIC_REPLICATIONS_KEY -> JsNumber(obj.offsetTopicReplications),
              JAR_INFOS_KEY -> JsArray(obj.jarInfos.map(FILE_INFO_JSON_FORMAT.write).toVector),
              CONNECTORS_KEY -> JsArray(obj.connectors.map(Definition.DEFINITION_JSON_FORMAT.write).toVector),
              NODE_NAMES_KEY -> JsArray(obj.nodeNames.map(JsString(_)).toVector),
              DEAD_NODES_KEY -> JsArray(obj.deadNodes.map(JsString(_)).toVector),
              TAGS_KEY -> JsObject(obj.tags),
              LASTMODIFIED_KEY -> JsNumber(obj.lastModified),
              STATE_KEY -> obj.state.fold[JsValue](JsNull)(JsString(_)),
              ERROR_KEY -> obj.error.fold[JsValue](JsNull)(JsString(_))
            ))
        )

        implicit val DEFINITION_JSON_FORMAT: OharaJsonFormat[Definition] = Definition.DEFINITION_JSON_FORMAT
        override def read(json: JsValue): WorkerClusterInfo = WorkerClusterInfo(
          name = noJsNull(json)(NAME_KEY).convertTo[String],
          imageName = noJsNull(json)(IMAGE_NAME_KEY).convertTo[String],
          brokerClusterName = noJsNull(json)(BROKER_CLUSTER_NAME_KEY).convertTo[String],
          clientPort = noJsNull(json)(CLIENT_PORT_KEY).convertTo[Int],
          jmxPort = noJsNull(json)(JMX_PORT_KEY).convertTo[Int],
          groupId = noJsNull(json)(GROUP_ID_KEY).convertTo[String],
          statusTopicName = noJsNull(json)(STATUS_TOPIC_NAME_KEY).convertTo[String],
          statusTopicPartitions = noJsNull(json)(STATUS_TOPIC_PARTITIONS_KEY).convertTo[Int],
          statusTopicReplications = noJsNull(json)(STATUS_TOPIC_REPLICATIONS_KEY).convertTo[Short],
          configTopicName = noJsNull(json)(CONFIG_TOPIC_NAME_KEY).convertTo[String],
          configTopicPartitions = noJsNull(json)(CONFIG_TOPIC_PARTITIONS_KEY).convertTo[Int],
          configTopicReplications = noJsNull(json)(CONFIG_TOPIC_REPLICATIONS_KEY).convertTo[Short],
          offsetTopicName = noJsNull(json)(OFFSET_TOPIC_NAME_KEY).convertTo[String],
          offsetTopicPartitions = noJsNull(json)(OFFSET_TOPIC_PARTITIONS_KEY).convertTo[Int],
          offsetTopicReplications = noJsNull(json)(OFFSET_TOPIC_REPLICATIONS_KEY).convertTo[Short],
          jarInfos = noJsNull(json)(JAR_INFOS_KEY).convertTo[Seq[FileInfo]],
          connectors = noJsNull(json)(CONNECTORS_KEY).convertTo[Seq[Definition]],
          nodeNames = noJsNull(json)(NODE_NAMES_KEY).convertTo[Seq[String]].toSet,
          deadNodes = noJsNull(json)(DEAD_NODES_KEY).convertTo[Seq[String]].toSet,
          tags = noJsNull(json)(TAGS_KEY).asJsObject.fields,
          lastModified = noJsNull(json)(LASTMODIFIED_KEY).convertTo[Long],
          state = noJsNull(json).get(STATE_KEY).map(_.convertTo[String]),
          error = noJsNull(json).get(ERROR_KEY).map(_.convertTo[String])
        )
      })
      .refine

  /**
    * used to generate the payload and url for POST/PUT request.
    */
  sealed trait Request {
    @Optional("default name is a random string")
    def name(name: String): Request

    @Optional("the default image is IMAGE_NAME_DEFAULT")
    def imageName(imageName: String): Request

    @Optional("the default port is random")
    def clientPort(clientPort: Int): Request

    @Optional("the default port is random")
    def jmxPort(jmxPort: Int): Request

    @Optional("Ignoring the name will invoke an auto-mapping to existent broker cluster")
    def brokerClusterName(brokerClusterName: String): Request

    @Optional("the default port is random")
    def groupId(groupId: String): Request
    @Optional("the default port is random")
    def statusTopicName(statusTopicName: String): Request
    @Optional("the default number is 1")
    def statusTopicPartitions(statusTopicPartitions: Int): Request
    @Optional("the default number is 1")
    def statusTopicReplications(statusTopicReplications: Short): Request
    @Optional("the default number is random")
    def configTopicName(configTopicName: String): Request
    @Optional("the default number is 1")
    def configTopicReplications(configTopicReplications: Short): Request
    def offsetTopicName(offsetTopicName: String): Request
    @Optional("the default number is 1")
    def offsetTopicPartitions(offsetTopicPartitions: Int): Request
    @Optional("the default number is 1")
    def offsetTopicReplications(offsetTopicReplications: Short): Request
    @Optional("the default value is empty")
    def jarKeys(jarKeys: Set[ObjectKey]): Request
    def nodeName(nodeName: String): Request = nodeNames(Set(CommonUtils.requireNonEmpty(nodeName)))
    def nodeNames(nodeNames: Set[String]): Request
    @Optional("default value is empty array in creation and None in update")
    def tags(tags: Map[String, JsValue]): Request

    /**
      * generate the POST request
      * @param executionContext thread pool
      * @return created data
      */
    def create()(implicit executionContext: ExecutionContext): Future[WorkerClusterInfo]

    /**
      * generate the PUT request
      * @param executionContext execution context
      * @return updated/created data
      */
    def update()(implicit executionContext: ExecutionContext): Future[WorkerClusterInfo]

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

  final class Access private[WorkerApi] extends ClusterAccess[WorkerClusterInfo](WORKER_PREFIX_PATH, GROUP_DEFAULT) {
    def request: Request = new Request {
      private[this] var name: String = CommonUtils.randomString(LIMIT_OF_NAME_LENGTH)
      private[this] var imageName: Option[String] = None
      private[this] var brokerClusterName: Option[String] = None
      private[this] var clientPort: Option[Int] = None
      private[this] var jmxPort: Option[Int] = None
      private[this] var groupId: Option[String] = None
      private[this] var configTopicName: Option[String] = None
      private[this] var configTopicReplications: Option[Short] = None
      private[this] var offsetTopicName: Option[String] = None
      private[this] var offsetTopicPartitions: Option[Int] = None
      private[this] var offsetTopicReplications: Option[Short] = None
      private[this] var statusTopicName: Option[String] = None
      private[this] var statusTopicPartitions: Option[Int] = None
      private[this] var statusTopicReplications: Option[Short] = None
      private[this] var jarKeys: Option[Set[ObjectKey]] = None
      private[this] var nodeNames: Option[Set[String]] = None
      private[this] var tags: Map[String, JsValue] = _

      private[this] def legalNumber(number: Int, key: String): Int = {
        if (number <= 0) throw new IllegalArgumentException(s"the number of $key must be bigger than zero")
        number
      }

      private[this] def legalNumber(number: Short, key: String): Short = {
        if (number <= 0) throw new IllegalArgumentException(s"the number of $key must be bigger than zero")
        number
      }

      override def name(name: String): Request = {
        this.name = CommonUtils.requireNonEmpty(name)
        this
      }

      override def imageName(imageName: String): Request = {
        this.imageName = Some(CommonUtils.requireNonEmpty(imageName))
        this
      }

      override def clientPort(clientPort: Int): Request = {
        this.clientPort = Some(CommonUtils.requireConnectionPort(clientPort))
        this
      }

      override def jmxPort(jmxPort: Int): Request = {
        this.jmxPort = Some(CommonUtils.requireConnectionPort(jmxPort))
        this
      }

      override def brokerClusterName(brokerClusterName: String): Request = {
        this.brokerClusterName = Some(CommonUtils.requireNonEmpty(brokerClusterName))
        this
      }

      override def groupId(groupId: String): Request = {
        this.groupId = Some(CommonUtils.requireNonEmpty(groupId))
        this
      }

      override def statusTopicName(statusTopicName: String): Request = {
        this.statusTopicName = Some(CommonUtils.requireNonEmpty(statusTopicName))
        this
      }

      override def statusTopicPartitions(statusTopicPartitions: Int): Request = {
        this.statusTopicPartitions = Some(legalNumber(statusTopicPartitions, "statusTopicPartitions"))
        this
      }

      override def statusTopicReplications(statusTopicReplications: Short): Request = {
        this.statusTopicReplications = Some(legalNumber(statusTopicReplications, "statusTopicReplications"))
        this
      }

      override def configTopicName(configTopicName: String): Request = {
        this.configTopicName = Some(CommonUtils.requireNonEmpty(configTopicName))
        this
      }

      override def configTopicReplications(configTopicReplications: Short): Request = {
        this.configTopicReplications = Some(legalNumber(configTopicReplications, "configTopicReplications"))
        this
      }

      override def offsetTopicName(offsetTopicName: String): Request = {
        this.offsetTopicName = Some(CommonUtils.requireNonEmpty(offsetTopicName))
        this
      }

      override def offsetTopicPartitions(offsetTopicPartitions: Int): Request = {
        this.offsetTopicPartitions = Some(legalNumber(offsetTopicPartitions, "offsetTopicPartitions"))
        this
      }

      override def offsetTopicReplications(offsetTopicReplications: Short): Request = {
        this.offsetTopicReplications = Some(legalNumber(offsetTopicReplications, "offsetTopicReplications"))
        this
      }

      import scala.collection.JavaConverters._
      override def jarKeys(jarKeys: Set[ObjectKey]): Request = {
        this.jarKeys = Some(CommonUtils.requireNonEmpty(jarKeys.asJava).asScala.toSet)
        this
      }

      override def nodeNames(nodeNames: Set[String]): Request = {
        this.nodeNames = Some(CommonUtils.requireNonEmpty(nodeNames.asJava).asScala.toSet)
        this
      }

      override def tags(tags: Map[String, JsValue]): Request = {
        this.tags = Objects.requireNonNull(tags)
        this
      }

      override private[v0] def creation = Creation(
        name = CommonUtils.requireNonEmpty(name),
        imageName = CommonUtils.requireNonEmpty(imageName.getOrElse(IMAGE_NAME_DEFAULT)),
        brokerClusterName = brokerClusterName.map(CommonUtils.requireNonEmpty),
        clientPort = CommonUtils.requireConnectionPort(clientPort.getOrElse(CommonUtils.availablePort())),
        jmxPort = CommonUtils.requireConnectionPort(jmxPort.getOrElse(CommonUtils.availablePort())),
        groupId = CommonUtils.requireNonEmpty(groupId.getOrElse(CommonUtils.randomString(10))),
        configTopicName =
          CommonUtils.requireNonEmpty(configTopicName.getOrElse(s"$groupId-config-${CommonUtils.randomString(10)}")),
        configTopicReplications = legalNumber(configTopicReplications.getOrElse(1), "configTopicReplications"),
        offsetTopicName =
          CommonUtils.requireNonEmpty(offsetTopicName.getOrElse(s"$groupId-offset-${CommonUtils.randomString(10)}")),
        offsetTopicPartitions = legalNumber(offsetTopicPartitions.getOrElse(1), "offsetTopicPartitions"),
        offsetTopicReplications = legalNumber(offsetTopicReplications.getOrElse(1), "offsetTopicReplications"),
        statusTopicName =
          CommonUtils.requireNonEmpty(statusTopicName.getOrElse(s"$groupId-status-${CommonUtils.randomString(10)}")),
        statusTopicPartitions = legalNumber(statusTopicPartitions.getOrElse(1), "statusTopicPartitions"),
        statusTopicReplications = legalNumber(statusTopicReplications.getOrElse(1), "statusTopicReplications"),
        jarKeys = Objects.requireNonNull(jarKeys.getOrElse(Set.empty)),
        nodeNames = CommonUtils.requireNonEmpty(nodeNames.getOrElse(Set.empty).asJava).asScala.toSet,
        tags = if (tags == null) Map.empty else tags
      )

      override private[v0] def update: Update = Update(
        imageName = imageName.map(CommonUtils.requireNonEmpty),
        brokerClusterName = brokerClusterName.map(CommonUtils.requireNonEmpty),
        clientPort = clientPort.map(CommonUtils.requireConnectionPort),
        jmxPort = jmxPort.map(CommonUtils.requireConnectionPort),
        groupId = groupId.map(CommonUtils.requireNonEmpty),
        configTopicName = configTopicName.map(CommonUtils.requireNonEmpty),
        configTopicReplications = configTopicReplications.map(legalNumber(_, "configTopicReplications")),
        offsetTopicName = offsetTopicName.map(CommonUtils.requireNonEmpty),
        offsetTopicPartitions = offsetTopicPartitions.map(legalNumber(_, "offsetTopicPartitions")),
        offsetTopicReplications = offsetTopicReplications.map(legalNumber(_, "offsetTopicReplications")),
        statusTopicName = statusTopicName.map(CommonUtils.requireNonEmpty),
        statusTopicPartitions = statusTopicPartitions.map(legalNumber(_, "statusTopicPartitions")),
        statusTopicReplications = statusTopicReplications.map(legalNumber(_, "statusTopicReplications")),
        jarKeys = jarKeys.map(key => Objects.requireNonNull(key)),
        nodeNames = nodeNames.map(seq => CommonUtils.requireNonEmpty(seq.asJava).asScala.toSet),
        tags = Option(tags)
      )

      override def create()(implicit executionContext: ExecutionContext): Future[WorkerClusterInfo] =
        exec.post[Creation, WorkerClusterInfo, ErrorApi.Error](
          url,
          creation
        )

      override def update()(implicit executionContext: ExecutionContext): Future[WorkerClusterInfo] =
        exec.put[Update, WorkerClusterInfo, ErrorApi.Error](
          s"$url/${CommonUtils.requireNonEmpty(name)}",
          update
        )
    }
  }

  def access: Access = new Access
}
