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
class TestHdfsInfoApi extends OharaTest with Matchers {

  @Test
  def nullKeyInGet(): Unit =
    an[NullPointerException] should be thrownBy HdfsInfoApi.access.get(null)

  @Test
  def nullKeyInDelete(): Unit =
    an[NullPointerException] should be thrownBy HdfsInfoApi.access.delete(null)

  @Test
  def testNullUriInUpdate(): Unit = {
    val update = HdfsInfoApi.HDFS_UPDATING_JSON_FORMAT.read("""
        |{
        | "uri": null
        |}
      """.stripMargin.parseJson)
    update.uri shouldBe None
  }

  @Test
  def testEmptyUriInUpdate(): Unit = {
    an[DeserializationException] should be thrownBy HdfsInfoApi.HDFS_UPDATING_JSON_FORMAT.read("""
        |{
        | "uri": ""
        |}
      """.stripMargin.parseJson)
  }

  @Test
  def testEmptyNameInCreation(): Unit = {
    an[DeserializationException] should be thrownBy HdfsInfoApi.HDFS_CREATION_JSON_FORMAT.read("""
        |{
        | "name": "",
        | "uri": "file:///tmp"
        |}
      """.stripMargin.parseJson)
  }

  @Test
  def testEmptyUriInCreation(): Unit = {
    an[DeserializationException] should be thrownBy HdfsInfoApi.HDFS_CREATION_JSON_FORMAT.read("""
        |{
        | "name": "hdfs_name1",
        | "uri": ""
        |}
      """.stripMargin.parseJson)

  }

  @Test
  def testNullUriInCreation(): Unit = {
    an[DeserializationException] should be thrownBy HdfsInfoApi.HDFS_CREATION_JSON_FORMAT.read("""
        |{
        | "name": "hdfs_name1",
        | "uri": null
        |}
        |""".stripMargin.parseJson)
  }

  @Test
  def testParserUpdate(): Unit = {
    val uri = s"file:///tmp/${CommonUtils.randomString()}"
    val update = HdfsInfoApi.HDFS_UPDATING_JSON_FORMAT.read(s"""
         |{
         | "uri": "$uri"
         |}
       """.stripMargin.parseJson)
    update.uri.get shouldBe uri
  }

  @Test
  def testParseCreation(): Unit = {
    val uri = s"file:///tmp/${CommonUtils.randomString()}"
    val creation = HdfsInfoApi.HDFS_CREATION_JSON_FORMAT.read(s"""
                                                               |{
                                                               | "uri": "$uri"
                                                               |}
       """.stripMargin.parseJson)
    creation.group shouldBe GROUP_DEFAULT
    creation.name.length shouldBe LIMIT_OF_KEY_LENGTH / 2
    creation.uri shouldBe uri

    val group = CommonUtils.randomString()
    val name = CommonUtils.randomString()
    val creation2 = HdfsInfoApi.HDFS_CREATION_JSON_FORMAT.read(s"""
         |{
         | "group": "$group",
         | "name": "$name",
         | "uri": "$uri"
         |}
       """.stripMargin.parseJson)
    creation2.group shouldBe group
    creation2.name shouldBe name
    creation2.uri shouldBe uri
  }

  @Test
  def ignoreNameOnCreation(): Unit = HdfsInfoApi.access
    .hostname(CommonUtils.randomString())
    .port(CommonUtils.availablePort())
    .request
    .uri(CommonUtils.randomString())
    .creation
    .name
    .length should not be 0

  @Test
  def ignoreNameOnUpdate(): Unit = an[NullPointerException] should be thrownBy HdfsInfoApi.access
    .hostname(CommonUtils.randomString())
    .port(CommonUtils.availablePort())
    .request
    .uri(CommonUtils.randomString())
    .update()

  @Test
  def emptyGroup(): Unit = an[IllegalArgumentException] should be thrownBy HdfsInfoApi.access.request.group("")

  @Test
  def nullGroup(): Unit = an[NullPointerException] should be thrownBy HdfsInfoApi.access.request.group(null)

  @Test
  def emptyName(): Unit = an[IllegalArgumentException] should be thrownBy HdfsInfoApi.access.request.name("")

  @Test
  def nullName(): Unit = an[NullPointerException] should be thrownBy HdfsInfoApi.access.request.name(null)

  @Test
  def ignoreUriOnCreation(): Unit = an[NullPointerException] should be thrownBy HdfsInfoApi.access
    .hostname(CommonUtils.randomString())
    .port(CommonUtils.availablePort())
    .request
    .name(CommonUtils.randomString())
    .create()

  @Test
  def emptyUri(): Unit = an[IllegalArgumentException] should be thrownBy HdfsInfoApi.access.request.uri("")

  @Test
  def nullUri(): Unit = an[NullPointerException] should be thrownBy HdfsInfoApi.access.request.uri(null)

  @Test
  def nullTags(): Unit = an[NullPointerException] should be thrownBy HdfsInfoApi.access.request.tags(null)

  @Test
  def emptyTags(): Unit = HdfsInfoApi.access.request.tags(Map.empty)

  @Test
  def testNameLimit(): Unit = an[DeserializationException] should be thrownBy
    HdfsInfoApi.access
      .hostname(CommonUtils.randomString())
      .port(CommonUtils.availablePort())
      .request
      .name(CommonUtils.randomString(LIMIT_OF_KEY_LENGTH))
      .group(CommonUtils.randomString(LIMIT_OF_KEY_LENGTH))
      .uri(CommonUtils.randomString())
      .creation
}
