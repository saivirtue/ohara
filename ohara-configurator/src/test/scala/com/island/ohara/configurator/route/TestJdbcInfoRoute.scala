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

package com.island.ohara.configurator.route

import com.island.ohara.client.configurator.v0.JdbcInfoApi
import com.island.ohara.client.configurator.v0.JdbcInfoApi.{JdbcInfo, Request}
import com.island.ohara.common.rule.OharaTest
import com.island.ohara.common.setting.ObjectKey
import com.island.ohara.common.util.{CommonUtils, Releasable}
import com.island.ohara.configurator.Configurator
import org.junit.{After, Test}
import org.scalatest.Matchers
import spray.json.{DeserializationException, JsNumber, JsString}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
class TestJdbcInfoRoute extends OharaTest with Matchers {
  private[this] val configurator = Configurator.builder.fake().build()

  private[this] val jdbcApi = JdbcInfoApi.access.hostname(configurator.hostname).port(configurator.port)

  private[this] def result[T](f: Future[T]): T = Await.result(f, Duration("20 seconds"))
  @Test
  def test(): Unit = {
    // test add
    result(jdbcApi.list()).size shouldBe 0

    val name = CommonUtils.randomString()
    val url = CommonUtils.randomString()
    val user = CommonUtils.randomString()
    val password = CommonUtils.randomString()
    val response = result(jdbcApi.request.name(name).jdbcUrl(url).user(user).password(password).create())
    response.name shouldBe name
    response.url shouldBe url
    response.user shouldBe user
    response.password shouldBe password

    // test get
    response shouldBe result(jdbcApi.get(response.key))

    // test update
    val url2 = CommonUtils.randomString()
    val user2 = CommonUtils.randomString()
    val password2 = CommonUtils.randomString()
    val response2 = result(jdbcApi.request.name(name).jdbcUrl(url2).user(user2).password(password2).update())
    response2.name shouldBe name
    response2.url shouldBe url2
    response2.user shouldBe user2
    response2.password shouldBe password2

    // test get
    response2 shouldBe result(jdbcApi.get(response2.key))

    // test delete
    result(jdbcApi.list()).size shouldBe 1
    result(jdbcApi.delete(response.key))
    result(jdbcApi.list()).size shouldBe 0

    // test nonexistent data
    an[IllegalArgumentException] should be thrownBy result(
      jdbcApi.get(ObjectKey.of(CommonUtils.randomString(5), CommonUtils.randomString(5))))
  }

  @Test
  def duplicateDelete(): Unit =
    (0 to 10).foreach(_ =>
      result(jdbcApi.delete(ObjectKey.of(CommonUtils.randomString(5), CommonUtils.randomString(5)))))

  @Test
  def duplicateUpdate(): Unit = {
    val count = 10
    (0 until count).foreach(
      _ =>
        result(
          jdbcApi.request
            .name(CommonUtils.randomString())
            .jdbcUrl(CommonUtils.randomString())
            .user(CommonUtils.randomString())
            .password(CommonUtils.randomString())
            .update()))
    result(jdbcApi.list()).size shouldBe count
  }

  @Test
  def testInvalidNameOnUpdate(): Unit = {
    val invalidStrings = Seq("a@", "a=", "a\\", "a~", "a//")
    invalidStrings.foreach { invalidString =>
      an[IllegalArgumentException] should be thrownBy result(
        jdbcApi.request
          .name(invalidString)
          .jdbcUrl(CommonUtils.randomString())
          .user(CommonUtils.randomString())
          .password(CommonUtils.randomString())
          .update())
    }
  }

  @Test
  def testInvalidNameOnCreation(): Unit = {
    val invalidStrings = Seq("a@", "a=", "a\\", "a~", "a//")
    invalidStrings.foreach { invalidString =>
      an[DeserializationException] should be thrownBy result(
        jdbcApi.request
          .name(invalidString)
          .jdbcUrl(CommonUtils.randomString())
          .user(CommonUtils.randomString())
          .password(CommonUtils.randomString())
          .create())
    }
  }

  @Test
  def testUpdateUrl(): Unit = {
    val url = CommonUtils.randomString()
    updatePartOfField(_.jdbcUrl(url), _.copy(url = url))
  }

  @Test
  def testUpdateUser(): Unit = {
    val user = CommonUtils.randomString()
    updatePartOfField(_.user(user), _.copy(user = user))
  }

  @Test
  def testUpdatePassword(): Unit = {
    val password = CommonUtils.randomString()
    updatePartOfField(_.password(password), _.copy(password = password))
  }

  private[this] def updatePartOfField(req: Request => Request, _expected: JdbcInfo => JdbcInfo): Unit = {
    val previous = result(
      jdbcApi.request
        .name(CommonUtils.randomString())
        .jdbcUrl(CommonUtils.randomString())
        .user(CommonUtils.randomString())
        .password(CommonUtils.randomString())
        .update())
    val updated = result(req(jdbcApi.request.name(previous.name)).update())
    val expected = _expected(previous)
    updated.name shouldBe expected.name
    updated.url shouldBe expected.url
    updated.user shouldBe expected.user
    updated.password shouldBe expected.password
  }

  @Test
  def updateTags(): Unit = {
    val tags = Map(
      CommonUtils.randomString(10) -> JsString(CommonUtils.randomString(10)),
      CommonUtils.randomString(10) -> JsNumber(CommonUtils.randomInteger())
    )
    val jdbcDesc = result(jdbcApi.request.jdbcUrl("url").user("user").password("password").tags(tags).create())
    jdbcDesc.tags shouldBe tags

    val tags2 = Map(
      CommonUtils.randomString(10) -> JsString(CommonUtils.randomString(10)),
      CommonUtils.randomString(10) -> JsNumber(CommonUtils.randomInteger())
    )
    val jdbcDesc2 = result(jdbcApi.request.name(jdbcDesc.name).tags(tags2).update())
    jdbcDesc2.tags shouldBe tags2

    val jdbcDesc3 = result(jdbcApi.request.name(jdbcDesc.name).update())
    jdbcDesc3.tags shouldBe tags2

    val jdbcDesc4 = result(jdbcApi.request.name(jdbcDesc.name).tags(Map.empty).update())
    jdbcDesc4.tags shouldBe Map.empty
  }

  @Test
  def testGroup(): Unit = {
    // default group
    result(
      jdbcApi.request
        .jdbcUrl("url")
        .user(CommonUtils.randomString())
        .password(CommonUtils.randomString())
        .create()).group shouldBe com.island.ohara.client.configurator.v0.GROUP_DEFAULT

    val group = CommonUtils.randomString()
    val ftpInfo = result(
      jdbcApi.request
        .group(group)
        .jdbcUrl("url")
        .user(CommonUtils.randomString())
        .password(CommonUtils.randomString())
        .create())
    ftpInfo.group shouldBe group

    result(jdbcApi.list()).size shouldBe 2

    // update an existent object
    result(
      jdbcApi.request
        .group(ftpInfo.group)
        .name(ftpInfo.name)
        .jdbcUrl("url")
        .user(CommonUtils.randomString())
        .password(CommonUtils.randomString())
        .update())

    result(jdbcApi.list()).size shouldBe 2

    // update an nonexistent (different group) object
    val group2 = CommonUtils.randomString()
    result(
      jdbcApi.request
        .group(group2)
        .name(ftpInfo.name)
        .jdbcUrl("url")
        .user(CommonUtils.randomString())
        .password(CommonUtils.randomString())
        .create()).group shouldBe group2

    result(jdbcApi.list()).size shouldBe 3
  }

  @After
  def tearDown(): Unit = Releasable.close(configurator)
}
