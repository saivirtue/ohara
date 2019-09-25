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
import com.island.ohara.common.annotations.Optional
import com.island.ohara.common.setting.ObjectKey
import com.island.ohara.common.util.CommonUtils
import spray.json.DefaultJsonProtocol._
import spray.json.{JsValue, RootJsonFormat}

import scala.concurrent.{ExecutionContext, Future}

object JdbcInfoApi {
  val JDBC_PREFIX_PATH: String = "jdbc"
  final case class Updating(url: Option[String],
                            user: Option[String],
                            password: Option[String],
                            tags: Option[Map[String, JsValue]])
  implicit val JDBC_UPDATING_JSON_FORMAT: RootJsonFormat[Updating] =
    JsonRefiner[Updating].format(jsonFormat4(Updating)).rejectEmptyString().refine

  final case class Creation(group: String,
                            name: String,
                            url: String,
                            user: String,
                            password: String,
                            tags: Map[String, JsValue])
      extends com.island.ohara.client.configurator.v0.BasicCreation
  implicit val JDBC_CREATION_JSON_FORMAT: OharaJsonFormat[Creation] =
    // this object is open to user define the (group, name) in UI, we need to handle the key rules
    basicRulesOfKey[Creation].format(jsonFormat6(Creation)).rejectEmptyString().nullToEmptyObject(TAGS_KEY).refine

  final case class JdbcInfo(group: String,
                            name: String,
                            url: String,
                            user: String,
                            password: String,
                            lastModified: Long,
                            tags: Map[String, JsValue])
      extends Data {
    override def kind: String = "jdbc"
  }
  implicit val JDBC_INFO_JSON_FORMAT: RootJsonFormat[JdbcInfo] = jsonFormat7(JdbcInfo)

  trait Request {

    /**
      * set the group and name via key
      * @param objectKey object key
      * @return this request
      */
    def key(objectKey: ObjectKey): Request = {
      group(objectKey.group())
      name(objectKey.name())
    }
    @Optional("default def is a GROUP_DEFAULT")
    def group(group: String): Request

    @Optional("default name is a random string. But it is required in updating")
    def name(name: String): Request

    @Optional("it is ignorable if you are going to send update request")
    def jdbcUrl(jdbcUrl: String): Request

    @Optional("it is ignorable if you are going to send update request")
    def user(user: String): Request

    @Optional("it is ignorable if you are going to send update request")
    def password(password: String): Request

    @Optional("default value is empty array")
    def tags(tags: Map[String, JsValue]): Request

    private[v0] def creation: Creation

    private[v0] def updating: Updating

    /**
      * generate the POST request
      * @param executionContext thread pool
      * @return created data
      */
    def create()(implicit executionContext: ExecutionContext): Future[JdbcInfo]

    /**
      * generate the PUT request
      * @param executionContext thread pool
      * @return updated/created data
      */
    def update()(implicit executionContext: ExecutionContext): Future[JdbcInfo]
  }

  class Access private[v0] extends com.island.ohara.client.configurator.v0.Access[JdbcInfo](JDBC_PREFIX_PATH) {
    def request: Request = new Request {
      private[this] var group: String = GROUP_DEFAULT
      private[this] var name: String = _
      private[this] var jdbcUrl: String = _
      private[this] var user: String = _
      private[this] var password: String = _
      private[this] var tags: Map[String, JsValue] = _

      override def group(group: String): Request = {
        this.group = CommonUtils.requireNonEmpty(group)
        this
      }

      override def name(name: String): Request = {
        this.name = CommonUtils.requireNonEmpty(name)
        this
      }

      override def jdbcUrl(jdbcUrl: String): Request = {
        this.jdbcUrl = CommonUtils.requireNonEmpty(jdbcUrl)
        this
      }

      override def user(user: String): Request = {
        this.user = CommonUtils.requireNonEmpty(user)
        this
      }

      override def password(password: String): Request = {
        this.password = CommonUtils.requireNonEmpty(password)
        this
      }

      override def tags(tags: Map[String, JsValue]): Request = {
        this.tags = Objects.requireNonNull(tags)
        this
      }

      override private[v0] def creation: Creation =
        // auto-complete the creation via our refiner
        JDBC_CREATION_JSON_FORMAT.read(
          JDBC_CREATION_JSON_FORMAT.write(Creation(
            group = CommonUtils.requireNonEmpty(group),
            name = if (CommonUtils.isEmpty(name)) CommonUtils.randomString(10) else name,
            url = CommonUtils.requireNonEmpty(jdbcUrl),
            user = CommonUtils.requireNonEmpty(user),
            password = CommonUtils.requireNonEmpty(password),
            tags = if (tags == null) Map.empty else tags
          )))

      override private[v0] def updating: Updating =
        // auto-complete the updating via our refiner
        JDBC_UPDATING_JSON_FORMAT.read(
          JDBC_UPDATING_JSON_FORMAT.write(Updating(
            url = Option(jdbcUrl).map(CommonUtils.requireNonEmpty),
            user = Option(user).map(CommonUtils.requireNonEmpty),
            password = Option(password).map(CommonUtils.requireNonEmpty),
            tags = Option(tags)
          )))

      override def create()(implicit executionContext: ExecutionContext): Future[JdbcInfo] =
        exec.post[Creation, JdbcInfo, ErrorApi.Error](
          url,
          creation
        )
      override def update()(implicit executionContext: ExecutionContext): Future[JdbcInfo] =
        exec.put[Updating, JdbcInfo, ErrorApi.Error](
          url(ObjectKey.of(group, name)),
          updating
        )
    }
  }

  def access: Access = new Access
}
