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
import com.island.ohara.client.kafka.WorkerJson.Plugin
import com.island.ohara.common.data.DataType
import com.island.ohara.common.setting.Definition
import com.island.ohara.kafka.connector.json.ConnectorDefUtils
import spray.json.DefaultJsonProtocol._
import spray.json.{JsString, JsValue, RootJsonFormat}

import scala.concurrent.{ExecutionContext, Future}

object InfoApi {

  /**
    * Provide a way to format DataType. Both Schema and Schema request use DataType.
    */
  implicit val DATA_TYPE_JSON_FORMAT: RootJsonFormat[DataType] = new RootJsonFormat[DataType] {
    override def write(obj: DataType): JsValue = JsString(obj.name)
    override def read(json: JsValue): DataType = DataType.valueOf(json.convertTo[String])
  }
  final case class ConfiguratorVersion(version: String, branch: String, user: String, revision: String, date: String)
  implicit val CONFIGURATOR_VERSION_JSON_FORMAT: RootJsonFormat[ConfiguratorVersion] = jsonFormat5(ConfiguratorVersion)

  val INFO_PREFIX_PATH: String = "info"

  final case class ConnectorVersion(className: String, typeName: String, version: String, revision: String)
  implicit val CONNECTOR_VERSION_JSON_FORMAT: RootJsonFormat[ConnectorVersion] = jsonFormat4(ConnectorVersion)

  def toConnectorVersion(pluginDescription: Definition): ConnectorVersion = {
    def orUnknown(f: => String): String = try f
    catch {
      case _: Throwable => "unknown"
    }
    ConnectorVersion(
      className = pluginDescription.className,
      version = orUnknown(ConnectorDefUtils.version(pluginDescription.definitions)),
      revision = orUnknown(ConnectorDefUtils.revision(pluginDescription.definitions)),
      typeName = orUnknown(ConnectorDefUtils.kind(pluginDescription.definitions))
    )
  }

  /**
    * ohara's official connectors have "specific" version information so we provide a way to convert it to
    * our version
    * @param plugin connector plugin
    * @return ConnectorVersion
    */
  def toConnectorVersion(plugin: Plugin): ConnectorVersion = {
    val (version, revision) = try {
      // see com.island.ohara.kafka.connection.Version for the format from "kafka's version"
      val index = plugin.version.lastIndexOf("_")
      if (index < 0 || index >= plugin.version.length - 1) (plugin.version, "unknown")
      else (plugin.version.substring(0, index), plugin.version.substring(index + 1))
    } catch {
      case _: Throwable => (plugin.version, "unknown")
    }
    ConnectorVersion(
      className = plugin.className,
      typeName = plugin.typeName,
      version = version,
      revision = revision
    )
  }

  final case class ConfiguratorInfo(versionInfo: ConfiguratorVersion, mode: String)
  sealed abstract class InfoAccess extends BasicAccess(INFO_PREFIX_PATH) {
    def get()(implicit executionContext: ExecutionContext): Future[ConfiguratorInfo]
  }
  implicit val CONFIGURATOR_INFO_JSON_FORMAT: RootJsonFormat[ConfiguratorInfo] = jsonFormat2(ConfiguratorInfo)

  def access: InfoAccess = new InfoAccess {
    override def get()(implicit executionContext: ExecutionContext): Future[ConfiguratorInfo] =
      exec.get[ConfiguratorInfo, ErrorApi.Error](url)
  }
}
