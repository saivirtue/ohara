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

import com.island.ohara.client.HttpExecutor
import com.island.ohara.client.configurator.ConfiguratorApiInfo
import com.island.ohara.common.setting.ObjectKey
import com.island.ohara.common.util.CommonUtils

import scala.concurrent.{ExecutionContext, Future}

/**
  * all accesses in v0 APIs need the hostname and port of remote node. This class implements the basic methods used to store the required
  * information to create the subclass access.
  *
  * @param prefixPath path to remote resource
  */
abstract class BasicAccess private[v0] (prefixPath: String) {
  protected[v0] val exec: HttpExecutor = HttpExecutor.SINGLETON
  // this access is under "v0" package so this field "version" is a constant string.
  private[this] val version = ConfiguratorApiInfo.V0
  private[this] var hostname: String = _
  private[this] var port: Int = -1

  def hostname(hostname: String): BasicAccess.this.type = {
    this.hostname = CommonUtils.requireNonEmpty(hostname)
    this
  }
  def port(port: Int): BasicAccess.this.type = {
    this.port = CommonUtils.requireConnectionPort(port)
    this
  }

  /**
    * Compose the url with hostname, port, version and prefix
    * @return url string
    */
  protected final def url: String = s"http://${CommonUtils.requireNonEmpty(hostname)}:${CommonUtils
    .requireConnectionPort(port)}/${CommonUtils.requireNonEmpty(version)}/${CommonUtils.requireNonEmpty(prefixPath)}"

  /**
    * used by UPDATE, DELETE and GET requests
    * @param key object key
    * @return url string
    */
  protected final def url(key: ObjectKey): String =
    s"$url/${key.name}?$GROUP_KEY=${key.group}"

  protected final def put(key: ObjectKey, action: String)(implicit executionContext: ExecutionContext): Future[Unit] =
    exec.put[ErrorApi.Error](url(key, action))

  private[this] def toString(params: Map[String, String]): String = {
    val paramString = params
      .map {
        case (key, value) => s"$key=$value"
      }
      .mkString("&")
    if (paramString.nonEmpty) s"&$paramString"
    else paramString
  }

  /**
    * used by START, STOP, PAUSE and RESUME requests.
    * the form is shown below:
    * url/${key.name}?group=${key.group}&param0=${param0}
    * @param key object key
    * @return url string
    */
  protected final def url(key: ObjectKey, params: Map[String, String]): String =
    s"$url/${key.name}?$GROUP_KEY=${key.group}${toString(params)}"

  /**
    * used by START, STOP, PAUSE and RESUME requests.
    * the form is shown below:
    * url/${key.name}/$postFix?group=${key.group}&param0=${param0}
    * @param key object key
    * @param postFix action string
    * @return url string
    */
  protected final def url(key: ObjectKey, postFix: String, params: Map[String, String] = Map.empty): String =
    s"$url/${key.name}/${CommonUtils.requireNonEmpty(postFix)}?$GROUP_KEY=${key.group}${toString(params)}"
}
