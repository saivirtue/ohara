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

import com.island.ohara.client.configurator.Data
import com.island.ohara.client.configurator.v0.ValidationApi.ValidationReport
import com.island.ohara.common.annotations.{Optional, VisibleForTesting}
import com.island.ohara.common.setting.ObjectKey
import com.island.ohara.common.util.CommonUtils
import spray.json.DefaultJsonProtocol._
import spray.json.{JsString, JsValue, RootJsonFormat}

import scala.concurrent.{ExecutionContext, Future}
object NodeApi {

  // We use the hostname field as "spec.hostname" label in k8s, which has a limit length <= 63
  // also see https://kubernetes.io/docs/concepts/overview/working-with-objects/labels/#syntax-and-character-set
  val LIMIT_OF_HOSTNAME_LENGTH: Int = 63

  /**
    * node does not support group. However, we are in group world and there are many cases of inputting key (group, name)
    * to access resource. This method used to generate key for hostname of node.
    * @param hostname hostname
    * @return object key
    */
  def key(hostname: String): ObjectKey = ObjectKey.of(GROUP_DEFAULT, hostname)

  val NODES_PREFIX_PATH: String = "nodes"

  val ZOOKEEPER_SERVICE_NAME: String = "zookeeper"
  val BROKER_SERVICE_NAME: String = "broker"
  val WORKER_SERVICE_NAME: String = "connect-worker"

  case class Updating(port: Option[Int],
                      user: Option[String],
                      password: Option[String],
                      tags: Option[Map[String, JsValue]])
  implicit val NODE_UPDATING_JSON_FORMAT: RootJsonFormat[Updating] =
    JsonRefiner[Updating].format(jsonFormat4(Updating)).requireConnectionPort("port").rejectEmptyString().refine

  case class Creation(hostname: String,
                      port: Option[Int],
                      user: Option[String],
                      password: Option[String],
                      tags: Map[String, JsValue])
      extends com.island.ohara.client.configurator.v0.BasicCreation {
    override def group: String = GROUP_DEFAULT
    override def name: String = hostname
  }
  implicit val NODE_CREATION_JSON_FORMAT: OharaJsonFormat[Creation] =
    JsonRefiner[Creation]
      .format(jsonFormat5(Creation))
      // default implementation of node is ssh, we use "default ssh port" here
      .nullToInt("port", 22)
      .requireConnectionPort("port")
      .rejectEmptyString()
      .stringRestriction("hostname")
      .withNumber()
      .withCharset()
      .withDot()
      .withDash()
      .withLengthLimit(LIMIT_OF_HOSTNAME_LENGTH)
      .toRefiner
      .nullToEmptyObject(TAGS_KEY)
      .nullToAnotherValueOfKey("hostname", "name")
      .refine

  case class NodeService(name: String, clusterNames: Seq[String])
  implicit val NODE_SERVICE_JSON_FORMAT: RootJsonFormat[NodeService] = jsonFormat2(NodeService)

  /**
    * NOTED: the field "services" is filled at runtime. If you are in testing, it is ok to assign empty to it.
    */
  case class Node(hostname: String,
                  port: Option[Int],
                  user: Option[String],
                  password: Option[String],
                  services: Seq[NodeService],
                  lastModified: Long,
                  validationReport: Option[ValidationReport],
                  tags: Map[String, JsValue])
      extends Data {
    // Node does not support to define group
    override def group: String = GROUP_DEFAULT
    private[this] def msg(key: String): String = s"$key is required since Ohara Configurator is in ssh mode"
    def _port: Int = port.getOrElse(throw new NoSuchElementException(msg("port")))
    def _user: String = user.getOrElse(throw new NoSuchElementException(msg("user")))
    def _password: String = password.getOrElse(throw new NoSuchElementException(msg("password")))
    override def name: String = hostname
    override def kind: String = "node"
  }

  implicit val NODE_JSON_FORMAT: RootJsonFormat[Node] = new RootJsonFormat[Node] {
    private[this] val format = jsonFormat8(Node)
    override def read(json: JsValue): Node = format.read(json)

    // TODO: remove name from this object ... by chia
    override def write(obj: Node): JsValue = {
      val json = format.write(obj).asJsObject
      json.copy(fields = json.fields ++ Map("name" -> JsString(obj.hostname)))
    }
  }

  /**
    * used to generate the payload and url for POST/PUT request.
    */
  trait Request {
    def hostname(hostname: String): Request

    @Optional("it is ignorable if you are going to send update request")
    def port(port: Int): Request

    @Optional("it is ignorable if you are going to send update request")
    def user(user: String): Request

    @Optional("it is ignorable if you are going to send update request")
    def password(password: String): Request

    @Optional("default value is empty array")
    def tags(tags: Map[String, JsValue]): Request

    /**
      * Retrieve the inner object of request payload. Noted, it throw unchecked exception if you haven't filled all required fields
      * @return the payload of creation
      */
    @VisibleForTesting
    private[v0] def creation: Creation

    /**
      * Retrieve the inner object of request payload. Noted, it throw unchecked exception if you haven't filled all required fields
      * @return the payload of creation
      */
    @VisibleForTesting
    private[v0] def updating: Updating

    /**
      * generate the POST request
      * @param executionContext thread pool
      * @return created data
      */
    def create()(implicit executionContext: ExecutionContext): Future[Node]

    /**
      * generate the PUT request
      * @param executionContext thread pool
      * @return updated/created data
      */
    def update()(implicit executionContext: ExecutionContext): Future[Node]
  }

  class Access private[v0] extends com.island.ohara.client.configurator.v0.Access[Node](NODES_PREFIX_PATH) {
    def request: Request = new Request {
      private[this] var hostname: String = _
      private[this] var port: Option[Int] = None
      private[this] var user: Option[String] = None
      private[this] var password: Option[String] = None
      private[this] var tags: Map[String, JsValue] = _
      override def hostname(hostname: String): Request = {
        this.hostname = CommonUtils.requireNonEmpty(hostname)
        this
      }
      override def port(port: Int): Request = {
        this.port = Some(CommonUtils.requireConnectionPort(port))
        this
      }
      override def user(user: String): Request = {
        this.user = Some(CommonUtils.requireNonEmpty(user))
        this
      }
      override def password(password: String): Request = {
        this.password = Some(CommonUtils.requireNonEmpty(password))
        this
      }

      override def tags(tags: Map[String, JsValue]): Request = {
        this.tags = Objects.requireNonNull(tags)
        this
      }

      override private[v0] def creation: Creation =
        // auto-complete the creation via our refiner
        NODE_CREATION_JSON_FORMAT.read(
          NODE_CREATION_JSON_FORMAT.write(Creation(
            hostname = CommonUtils.requireNonEmpty(hostname),
            user = user.map(CommonUtils.requireNonEmpty),
            password = password.map(CommonUtils.requireNonEmpty),
            port = port.map(CommonUtils.requireConnectionPort),
            tags = if (tags == null) Map.empty else tags
          )))

      override private[v0] def updating: Updating =
        // auto-complete the updating via our refiner
        NODE_UPDATING_JSON_FORMAT.read(
          NODE_UPDATING_JSON_FORMAT.write(Updating(
            port = port.map(CommonUtils.requireConnectionPort),
            user = user.map(CommonUtils.requireNonEmpty),
            password = password.map(CommonUtils.requireNonEmpty),
            tags = Option(tags)
          )))

      override def create()(implicit executionContext: ExecutionContext): Future[Node] =
        exec.post[Creation, Node, ErrorApi.Error](
          url,
          creation
        )
      override def update()(implicit executionContext: ExecutionContext): Future[Node] =
        exec.put[Updating, Node, ErrorApi.Error](
          s"$url/${CommonUtils.requireNonEmpty(hostname)}",
          updating
        )
    }
  }

  def access: Access = new Access
}
