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
import com.island.ohara.common.util.{CommonUtils, VersionUtils}
import spray.json.DefaultJsonProtocol._
import spray.json.{JsArray, JsString, JsValue}

import scala.concurrent.{ExecutionContext, Future}

object ZookeeperApi {

  /**
    * The default value of group for this API.
    */
  val GROUP_DEFAULT: String = com.island.ohara.client.configurator.v0.GROUP_DEFAULT

  /**
    * docker does limit the length of name (< 64). Since we format container name with some part of prefix,
    * limit the name length to one-third of 64 chars should be suitable for most cases.
    */
  val LIMIT_OF_NAME_LENGTH: Int = 20

  val ZOOKEEPER_PREFIX_PATH: String = "zookeepers"

  val ZOOKEEPER_SERVICE_NAME: String = "zk"

  /**
    * the default docker image used to run containers of worker cluster
    */
  val IMAGE_NAME_DEFAULT: String = s"oharastream/zookeeper:${VersionUtils.VERSION}"

  final case class Creation private[ZookeeperApi] (name: String,
                                                   imageName: String,
                                                   clientPort: Int,
                                                   peerPort: Int,
                                                   electionPort: Int,
                                                   nodeNames: Set[String],
                                                   tags: Map[String, JsValue])
      extends ClusterCreationRequest {
    override def group: String = GROUP_DEFAULT
    override def ports: Set[Int] = Set(clientPort, peerPort, electionPort)
  }

  /**
    * exposed to configurator
    */
  private[ohara] implicit val ZOOKEEPER_CREATION_JSON_FORMAT: OharaJsonFormat[Creation] =
    ClusterJsonRefiner
      .basicRulesOfCreation[Creation](IMAGE_NAME_DEFAULT)
      .format(jsonFormat7(Creation))
      .nullToRandomPort("clientPort")
      .requireBindPort("clientPort")
      .nullToRandomPort("peerPort")
      .requireBindPort("peerPort")
      .nullToRandomPort("electionPort")
      .requireBindPort("electionPort")
      .refine

  final case class Update private[ZookeeperApi] (imageName: Option[String],
                                                 clientPort: Option[Int],
                                                 peerPort: Option[Int],
                                                 electionPort: Option[Int],
                                                 nodeNames: Option[Set[String]],
                                                 tags: Option[Map[String, JsValue]])
      extends ClusterUpdateRequest
  implicit val ZOOKEEPER_UPDATE_JSON_FORMAT: OharaJsonFormat[Update] =
    ClusterJsonRefiner
      .basicRulesOfUpdate[Update]
      .format(jsonFormat6(Update))
      .requireBindPort("clientPort")
      .requireBindPort("peerPort")
      .requireBindPort("electionPort")
      .refine

  final case class ZookeeperClusterInfo private[ZookeeperApi] (name: String,
                                                               imageName: String,
                                                               clientPort: Int,
                                                               peerPort: Int,
                                                               electionPort: Int,
                                                               nodeNames: Set[String],
                                                               deadNodes: Set[String],
                                                               tags: Map[String, JsValue],
                                                               lastModified: Long,
                                                               state: Option[String],
                                                               error: Option[String])
      extends ClusterInfo {
    // cluster does not support to define group
    override def group: String = GROUP_DEFAULT
    override def kind: String = ZOOKEEPER_SERVICE_NAME
    override def ports: Set[Int] = Set(clientPort, peerPort, electionPort)
    override def clone(newNodeNames: Set[String]): ClusterInfo = this.copy(nodeNames = newNodeNames)
  }

  /**
    * exposed to configurator
    */
  private[ohara] implicit val ZOOKEEPER_CLUSTER_INFO_JSON_FORMAT: OharaJsonFormat[ZookeeperClusterInfo] =
    JsonRefiner[ZookeeperClusterInfo].format(jsonFormat11(ZookeeperClusterInfo)).refine

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
    def peerPort(clientPort: Int): Request
    @Optional("the default port is random")
    def electionPort(clientPort: Int): Request
    def nodeName(nodeName: String): Request = nodeNames(Set(CommonUtils.requireNonEmpty(nodeName)))
    def nodeNames(nodeNames: Set[String]): Request
    @Optional("default value is empty array in creation and None in update")
    def tags(tags: Map[String, JsValue]): Request

    /**
      * generate the POST request
      * @param executionContext thread pool
      * @return created data
      */
    def create()(implicit executionContext: ExecutionContext): Future[ZookeeperClusterInfo]

    /**
      * generate the PUT request
      * @param executionContext execution context
      * @return updated/created data
      */
    def update()(implicit executionContext: ExecutionContext): Future[ZookeeperClusterInfo]

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

  final class Access private[ZookeeperApi]
      extends ClusterAccess[ZookeeperClusterInfo](ZOOKEEPER_PREFIX_PATH, GROUP_DEFAULT) {
    def request: Request = new Request {
      private[this] var name: String = CommonUtils.randomString(LIMIT_OF_NAME_LENGTH)
      private[this] var imageName: Option[String] = None
      private[this] var clientPort: Option[Int] = None
      private[this] var peerPort: Option[Int] = None
      private[this] var electionPort: Option[Int] = None
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

      override def clientPort(clientPort: Int): Request = {
        this.clientPort = Some(CommonUtils.requireConnectionPort(clientPort))
        this
      }

      override def peerPort(peerPort: Int): Request = {
        this.peerPort = Some(CommonUtils.requireConnectionPort(peerPort))
        this
      }

      override def electionPort(electionPort: Int): Request = {
        this.electionPort = Some(CommonUtils.requireConnectionPort(electionPort))
        this
      }

      import scala.collection.JavaConverters._
      override def nodeNames(nodeNames: Set[String]): Request = {
        this.nodeNames = Some(CommonUtils.requireNonEmpty(nodeNames.asJava).asScala.toSet)
        ZOOKEEPER_CREATION_JSON_FORMAT.check("nodeNames", JsArray(nodeNames.map(JsString(_)).toVector))
        this
      }

      override def tags(tags: Map[String, JsValue]): Request = {
        this.tags = Objects.requireNonNull(tags)
        this
      }

      override private[v0] def creation: Creation = Creation(
        name = CommonUtils.requireNonEmpty(name),
        imageName = CommonUtils.requireNonEmpty(imageName.getOrElse(IMAGE_NAME_DEFAULT)),
        clientPort = CommonUtils.requireConnectionPort(clientPort.getOrElse(CommonUtils.availablePort())),
        peerPort = CommonUtils.requireConnectionPort(peerPort.getOrElse(CommonUtils.availablePort())),
        electionPort = CommonUtils.requireConnectionPort(electionPort.getOrElse(CommonUtils.availablePort())),
        nodeNames = CommonUtils.requireNonEmpty(nodeNames.getOrElse(Set.empty).asJava).asScala.toSet,
        tags = if (tags == null) Map.empty else tags
      )

      override private[v0] def update: Update = Update(
        imageName = imageName.map(CommonUtils.requireNonEmpty),
        clientPort = clientPort.map(CommonUtils.requireConnectionPort),
        peerPort = peerPort.map(CommonUtils.requireConnectionPort),
        electionPort = electionPort.map(CommonUtils.requireConnectionPort),
        nodeNames = nodeNames.map(seq => CommonUtils.requireNonEmpty(seq.asJava).asScala.toSet),
        tags = Option(tags)
      )

      override def create()(implicit executionContext: ExecutionContext): Future[ZookeeperClusterInfo] =
        exec.post[Creation, ZookeeperClusterInfo, ErrorApi.Error](
          url,
          creation
        )

      override def update()(implicit executionContext: ExecutionContext): Future[ZookeeperClusterInfo] =
        exec.put[Update, ZookeeperClusterInfo, ErrorApi.Error](
          s"$url/${CommonUtils.requireNonEmpty(name)}",
          update
        )
    }
  }

  def access: Access = new Access
}
