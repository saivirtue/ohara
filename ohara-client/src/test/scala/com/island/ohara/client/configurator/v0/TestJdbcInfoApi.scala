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

import com.island.ohara.common.rule.OharaTest
import com.island.ohara.common.util.CommonUtils
import org.junit.Test
import org.scalatest.Matchers
import spray.json._
import scala.concurrent.ExecutionContext.Implicits.global

class TestJdbcInfoApi extends OharaTest with Matchers {

  @Test
  def nullKeyInGet(): Unit =
    an[NullPointerException] should be thrownBy JdbcInfoApi.access.get(null)

  @Test
  def nullKeyInDelete(): Unit =
    an[NullPointerException] should be thrownBy JdbcInfoApi.access.delete(null)

  @Test
  def testNullUrlInUpdate(): Unit = {
    val update = JdbcInfoApi.JDBC_UPDATING_JSON_FORMAT.read("""
        |{
        | "url": null
        |}
      """.stripMargin.parseJson)
    update.url shouldBe None
  }

  @Test
  def testNullUserInUpdate(): Unit = {
    val update = JdbcInfoApi.JDBC_UPDATING_JSON_FORMAT.read("""
        |{
        | "user": null
        |}
      """.stripMargin.parseJson)
    update.user shouldBe None
  }

  @Test
  def testNullPasswordInUpdate(): Unit = {
    val update = JdbcInfoApi.JDBC_UPDATING_JSON_FORMAT.read("""
        |{
        | "password": null
        |}
      """.stripMargin.parseJson)
    update.user shouldBe None
  }

  @Test
  def testEmptyUrlInUpdate(): Unit = {
    an[DeserializationException] should be thrownBy JdbcInfoApi.JDBC_UPDATING_JSON_FORMAT.read("""
        |{
        | "url": ""
        |}
      """.stripMargin.parseJson)
  }

  @Test
  def testEmptyUserInUpdate(): Unit = {
    an[DeserializationException] should be thrownBy JdbcInfoApi.JDBC_UPDATING_JSON_FORMAT.read("""
        |{
        | "user": ""
        |}
      """.stripMargin.parseJson)
  }

  @Test
  def testEmptyPasswordInUpdate(): Unit = {
    an[DeserializationException] should be thrownBy JdbcInfoApi.JDBC_UPDATING_JSON_FORMAT.read("""
        |{
        | "password": ""
        |}
      """.stripMargin.parseJson)
  }

  @Test
  def testNullNameInCreation(): Unit = {
    an[DeserializationException] should be thrownBy JdbcInfoApi.JDBC_CREATION_JSON_FORMAT.read("""
        |{
        | "name": null
        |}
      """.stripMargin.parseJson)
  }

  @Test
  def testNullUrlInCreation(): Unit = {
    an[DeserializationException] should be thrownBy JdbcInfoApi.JDBC_CREATION_JSON_FORMAT.read("""
        |{
        | "url": null
        |}
      """.stripMargin.parseJson)
  }

  @Test
  def testEmptyNameInCreation(): Unit = {
    an[DeserializationException] should be thrownBy JdbcInfoApi.JDBC_CREATION_JSON_FORMAT.read("""
        |{
        | "name": ""
        |}
      """.stripMargin.parseJson)
  }

  @Test
  def testEmptyUrlInCreation(): Unit = {
    an[DeserializationException] should be thrownBy JdbcInfoApi.JDBC_CREATION_JSON_FORMAT.read("""
        |{
        | "url": ""
        |}
      """.stripMargin.parseJson)
  }

  @Test
  def testEmptyUserInCreation(): Unit = {
    an[DeserializationException] should be thrownBy JdbcInfoApi.JDBC_CREATION_JSON_FORMAT.read("""
        |{
        | "user": ""
        |}
      """.stripMargin.parseJson)
  }

  @Test
  def testEmptyPasswordInCreation(): Unit = {
    an[DeserializationException] should be thrownBy JdbcInfoApi.JDBC_CREATION_JSON_FORMAT.read("""
        |{
        | "password": ""
        |}
      """.stripMargin.parseJson)
  }

  @Test
  def testParseUpdate(): Unit = {
    val url = CommonUtils.randomString()
    val user = CommonUtils.randomString()
    val password = CommonUtils.randomString()
    val update = JdbcInfoApi.JDBC_UPDATING_JSON_FORMAT.read(s"""
         |{
         | "url": "$url",
         | "user": "$user",
         | "password": "$password"
         |}
       """.stripMargin.parseJson)
    update.url.get shouldBe url
    update.user.get shouldBe user
    update.password.get shouldBe password
  }

  @Test
  def testParseCreation(): Unit = {
    val url = CommonUtils.randomString()
    val user = CommonUtils.randomString()
    val password = CommonUtils.randomString()
    val creation = JdbcInfoApi.JDBC_CREATION_JSON_FORMAT.read(s"""
                                                              |{
                                                              | "url": "$url",
                                                              | "user": "$user",
                                                              | "password": "$password"
                                                              |}
      """.stripMargin.parseJson)

    creation.group shouldBe GROUP_DEFAULT
    creation.name.length shouldBe LIMIT_OF_KEY_LENGTH / 2
    creation.url shouldBe url
    creation.user shouldBe user
    creation.password shouldBe password

    val group = CommonUtils.randomString()
    val name = CommonUtils.randomString()
    val creation2 = JdbcInfoApi.JDBC_CREATION_JSON_FORMAT.read(s"""
        |{
        | "group": "$group",
        | "name": "$name",
        | "url": "$url",
        | "user": "$user",
        | "password": "$password"
        |}
      """.stripMargin.parseJson)

    creation2.group shouldBe group
    creation2.name shouldBe name
    creation2.url shouldBe url
    creation2.user shouldBe user
    creation2.password shouldBe password
  }

  @Test
  def ignoreNameOnCreation(): Unit = JdbcInfoApi.access
    .hostname(CommonUtils.randomString())
    .port(CommonUtils.availablePort())
    .request
    .jdbcUrl(CommonUtils.randomString())
    .user(CommonUtils.randomString())
    .password(CommonUtils.randomString())
    .creation
    .name
    .length should not be 0

  @Test
  def ignoreNameOnUpdate(): Unit = an[NullPointerException] should be thrownBy JdbcInfoApi.access
    .hostname(CommonUtils.randomString())
    .port(CommonUtils.availablePort())
    .request
    .jdbcUrl(CommonUtils.randomString())
    .user(CommonUtils.randomString())
    .password(CommonUtils.randomString())
    .update()

  @Test
  def emptyGroup(): Unit = an[IllegalArgumentException] should be thrownBy JdbcInfoApi.access.request.group("")

  @Test
  def nullGroup(): Unit = an[NullPointerException] should be thrownBy JdbcInfoApi.access.request.group(null)

  @Test
  def emptyName(): Unit = an[IllegalArgumentException] should be thrownBy JdbcInfoApi.access.request.name("")

  @Test
  def nullName(): Unit = an[NullPointerException] should be thrownBy JdbcInfoApi.access.request.name(null)

  @Test
  def emptyUrl(): Unit = an[IllegalArgumentException] should be thrownBy JdbcInfoApi.access.request.jdbcUrl("")

  @Test
  def nullUrl(): Unit = an[NullPointerException] should be thrownBy JdbcInfoApi.access.request.jdbcUrl(null)

  @Test
  def ignoreUrlOnCreation(): Unit = an[NullPointerException] should be thrownBy JdbcInfoApi.access
    .hostname(CommonUtils.randomString())
    .port(CommonUtils.availablePort())
    .request
    .name(CommonUtils.randomString())
    .user(CommonUtils.randomString())
    .password(CommonUtils.randomString())
    .create()

  @Test
  def ignoreUserOnCreation(): Unit = an[NullPointerException] should be thrownBy JdbcInfoApi.access
    .hostname(CommonUtils.randomString())
    .port(CommonUtils.availablePort())
    .request
    .name(CommonUtils.randomString())
    .jdbcUrl(CommonUtils.randomString())
    .password(CommonUtils.randomString())
    .create()

  @Test
  def emptyUser(): Unit = an[IllegalArgumentException] should be thrownBy JdbcInfoApi.access.request.user("")

  @Test
  def nullUser(): Unit = an[NullPointerException] should be thrownBy JdbcInfoApi.access.request.user(null)

  @Test
  def ignorePasswordOnCreation(): Unit = an[NullPointerException] should be thrownBy JdbcInfoApi.access
    .hostname(CommonUtils.randomString())
    .port(CommonUtils.availablePort())
    .request
    .name(CommonUtils.randomString())
    .jdbcUrl(CommonUtils.randomString())
    .user(CommonUtils.randomString())
    .create()

  @Test
  def emptyPassword(): Unit =
    an[IllegalArgumentException] should be thrownBy JdbcInfoApi.access.request.password("")

  @Test
  def nullPassword(): Unit = an[NullPointerException] should be thrownBy JdbcInfoApi.access.request.password(null)

  @Test
  def nullTags(): Unit = an[NullPointerException] should be thrownBy JdbcInfoApi.access.request.tags(null)

  @Test
  def emptyTags(): Unit = JdbcInfoApi.access.request.tags(Map.empty)

  @Test
  def testNameLimit(): Unit = an[DeserializationException] should be thrownBy
    JdbcInfoApi.access
      .hostname(CommonUtils.randomString())
      .port(CommonUtils.availablePort())
      .request
      .name(CommonUtils.randomString(LIMIT_OF_KEY_LENGTH))
      .group(CommonUtils.randomString(LIMIT_OF_KEY_LENGTH))
      .user(CommonUtils.randomString())
      .password(CommonUtils.randomString())
      .jdbcUrl(CommonUtils.randomString())
      .creation
}
