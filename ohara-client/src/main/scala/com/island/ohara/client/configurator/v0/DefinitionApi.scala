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

import com.island.ohara.client.Enum
import com.island.ohara.client.configurator.v0.FileInfoApi.FileInfo
import com.island.ohara.common.annotations.Optional
import com.island.ohara.common.setting.Definition
import com.island.ohara.common.util.CommonUtils
import spray.json.DefaultJsonProtocol._
import spray.json.{JsObject, JsValue, RootJsonFormat, _}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
object DefinitionApi {

  /**
    * The default value of group for this API.
    */
  val GROUP_DEFAULT: String = com.island.ohara.client.configurator.v0.GROUP_DEFAULT
  val DEFINITION_PREFIX_PATH: String = "definitions"

  final case class Params private[DefinitionApi] (imageName: String, jarInfo: Option[FileInfo])
  implicit val PARAMS_JSON_FORMAT: OharaJsonFormat[Params] = JsonRefiner[Params]
    .format(jsonFormat2(Params))
    // image name could not be empty
    .rejectEmptyString()
    .refine

  implicit val DEFINITION_JSON_FORMAT: OharaJsonFormat[Definition] = JsonRefiner[Definition]
    .format(new RootJsonFormat[Definition] {
      override def write(obj: Definition): JsValue = JsObject(
        Definition.CLASS_NAME_KEY -> obj.className().toJson,
        Definition.DEFINITIONS_KEY -> obj.definitions().asScala.toVector.toJson
      )
      override def read(json: JsValue): Definition = Definition.ofJson(json.compactPrint)
    })
    // className could not be empty
    .rejectEmptyString()
    .refine

  abstract sealed class ServiceType(val name: String) extends Serializable {
    // override toString method to get the equals value for "forName()" method
    override def toString: String = this.name
  }
  final object ServiceType extends Enum[ServiceType] {
    case object Zookeeper extends ServiceType("zookeeper")
    case object Broker extends ServiceType("broker")
    case object Worker extends ServiceType("worker")
    case object Stream extends ServiceType("stream")
    case object Topic extends ServiceType("topic")
  }

  sealed trait Request {

    def serviceType(serviceType: ServiceType): Request

    def imageName(imageName: String): Request

    @Optional("default is empty")
    def jarInfo(jarInfo: FileInfo): Request

    private[DefinitionApi] def params: Params

    def fetch()(implicit executionContext: ExecutionContext): Future[Definition]
  }

  final class Access extends BasicAccess(DEFINITION_PREFIX_PATH) {
    def request: Request = new Request {
      private[this] var serviceType: ServiceType = _
      private[this] var imageName: String = _
      private[this] var jarInfo: Option[FileInfo] = None

      def serviceType(serviceType: ServiceType): Request = {
        this.serviceType = Objects.requireNonNull(serviceType)
        this
      }

      def imageName(imageName: String): Request = {
        this.imageName = CommonUtils.requireNonEmpty(imageName)
        this
      }

      def jarInfo(jarInfo: FileInfo): Request = {
        this.jarInfo = Some(Objects.requireNonNull(jarInfo))
        this
      }

      override private[DefinitionApi] def params = Params(
        imageName = CommonUtils.requireNonEmpty(imageName),
        jarInfo = jarInfo.map(info => Objects.requireNonNull(info))
      )

      /**
        * fetch the definition by the required payload.
        * @param executionContext thread pool
        * @return file info
        */
      def fetch()(implicit executionContext: ExecutionContext): Future[Definition] =
        exec.put[Params, Definition, ErrorApi.Error](
          s"$url/${CommonUtils.requireNonEmpty(serviceType.name, () => "serviceType could not be null")}",
          params)
    }
  }

  def access: Access = new Access
}
