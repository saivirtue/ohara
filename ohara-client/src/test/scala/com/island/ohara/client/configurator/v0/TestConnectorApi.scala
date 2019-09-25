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

import com.island.ohara.client.configurator.v0.ConnectorApi.State._
import com.island.ohara.client.configurator.v0.ConnectorApi._
import com.island.ohara.client.configurator.v0.MetricsApi.Metrics
import com.island.ohara.common.data.{Column, DataType}
import com.island.ohara.common.rule.OharaTest
import com.island.ohara.common.setting.{PropGroups, TopicKey}
import com.island.ohara.common.util.CommonUtils
import org.junit.Test
import org.scalatest.Matchers
import spray.json.DefaultJsonProtocol._
import spray.json.{JsArray, JsString, _}

import scala.concurrent.ExecutionContext.Implicits.global
class TestConnectorApi extends OharaTest with Matchers {

  @Test
  def nullKeyInGet(): Unit =
    an[NullPointerException] should be thrownBy ConnectorApi.access.get(null)

  @Test
  def nullKeyInDelete(): Unit =
    an[NullPointerException] should be thrownBy ConnectorApi.access.delete(null)

  @Test
  def testParseCreation(): Unit = {
    val workerClusterName = CommonUtils.randomString()
    val className = CommonUtils.randomString()
    val topicKeys = Set(TopicKey.of(CommonUtils.randomString(), CommonUtils.randomString()))
    val numberOfTasks = 10
    val tags = Map("a" -> JsString("b"), "b" -> JsNumber(1))
    val anotherKey = CommonUtils.randomString()
    val anotherValue = CommonUtils.randomString()

    val creation = CONNECTOR_CREATION_FORMAT.read(s"""
       |{
       |  "workerClusterName": ${JsString(workerClusterName).toString()},
       |  "connector.class": ${JsString(className).toString()},
       |  "numberOfTasks": ${JsNumber(numberOfTasks).toString()},
       |  "topicKeys": ${JsArray(topicKeys.map(TopicKey.toJsonString).map(_.parseJson).toVector).toString()},
       |  "tags": ${JsObject(tags)},
       |  "$anotherKey": "$anotherValue"
       |}
      """.stripMargin.parseJson)

    creation.group shouldBe GROUP_DEFAULT
    creation.name.length shouldBe LIMIT_OF_KEY_LENGTH / 2
    creation.workerClusterKey.get.name() shouldBe workerClusterName
    creation.className shouldBe className
    creation.columns shouldBe Seq.empty
    creation.topicKeys shouldBe topicKeys
    creation.numberOfTasks shouldBe 1
    creation.tags shouldBe tags
    // this key is deprecated so json converter will replace it by new one
    creation.settings.contains("className") shouldBe false
    creation.settings.contains("aaa") shouldBe false
    creation.settings(anotherKey).convertTo[String] shouldBe anotherValue
    CONNECTOR_CREATION_FORMAT.read(CONNECTOR_CREATION_FORMAT.write(creation)).settings shouldBe creation.settings

    val group = CommonUtils.randomString()
    val name = CommonUtils.randomString()
    val column = Column
      .builder()
      .name(CommonUtils.randomString())
      .newName(CommonUtils.randomString())
      .dataType(DataType.DOUBLE)
      .build()
    val creation2 = CONNECTOR_CREATION_FORMAT.read(s"""
       |{
       |  "group": "$group",
       |  "name": ${JsString(name).toString()},
       |  "workerClusterName": ${JsString(workerClusterName).toString()},
       |  "connector.class": ${JsString(className).toString()},
       |  "$COLUMNS_KEY": ${PropGroups.ofColumn(column).toJsonString},
       |  "topicKeys": ${JsArray(topicKeys.map(TopicKey.toJsonString).map(_.parseJson).toVector).toString()},
       |  "numberOfTasks": ${JsNumber(numberOfTasks).toString()},
       |  "$anotherKey": "$anotherValue"
       |}""".stripMargin.parseJson)
    creation2.group shouldBe group
    creation2.name shouldBe name
    creation2.workerClusterKey.get.name() shouldBe workerClusterName
    creation2.className shouldBe className
    creation2.columns shouldBe Seq(column)
    creation.topicKeys shouldBe topicKeys
    creation2.numberOfTasks shouldBe 1
    // this key is deprecated so json converter will replace it by new one
    creation2.settings.contains("className") shouldBe false
    creation2.settings.contains("aaa") shouldBe false
    creation2.settings(anotherKey).convertTo[String] shouldBe anotherValue
    CONNECTOR_CREATION_FORMAT.read(CONNECTOR_CREATION_FORMAT.write(creation2)).settings shouldBe creation2.settings
  }

  @Test
  def testState(): Unit = {
    State.all shouldBe Seq(
      UNASSIGNED,
      RUNNING,
      PAUSED,
      FAILED,
      DESTROYED
    ).sortBy(_.name)
  }

  @Test
  def testStateJson(): Unit = {
    State.all.foreach(state =>
      ConnectorApi.CONNECTOR_STATE_FORMAT.read(ConnectorApi.CONNECTOR_STATE_FORMAT.write(state)) shouldBe state)
  }

  @Test
  def renderJsonWithoutAnyRequiredFields(): Unit = {
    val response = ConnectorDescription(
      settings = Map(
        CommonUtils.randomString() -> JsString(CommonUtils.randomString()),
        GROUP_KEY -> JsString(CommonUtils.randomString()),
        NAME_KEY -> JsString(CommonUtils.randomString())
      ),
      status = None,
      tasksStatus = Seq.empty,
      metrics = Metrics.EMPTY,
      lastModified = CommonUtils.current()
    )
    // pass
    ConnectorApi.CONNECTOR_DESCRIPTION_FORMAT.write(response)
  }

  @Test
  def renderJsonWithConnectorClass(): Unit = {
    val className = CommonUtils.randomString()
    val response = ConnectorDescription(
      settings = access.request.className(className).creation.settings,
      status = None,
      tasksStatus = Seq.empty,
      metrics = Metrics.EMPTY,
      lastModified = CommonUtils.current()
    )
    ConnectorApi.CONNECTOR_DESCRIPTION_FORMAT
      .write(response)
      .asInstanceOf[JsObject]
      // previous name
      .fields
      .contains("className") shouldBe false
  }

  @Test
  def parsePreviousKeyOfClassNameFromConnectorDescription(): Unit = {
    import spray.json._
    val className = CommonUtils.randomString()
    val connectorDescription = ConnectorApi.CONNECTOR_DESCRIPTION_FORMAT.read(s"""
      |  {
      |    "id": "asdasdsad",
      |    "lastModified": 123,
      |    "settings": {
      |    "className": "$className"
      |    },
      |    "metrics": {
      |      "meters":[]
      |    },
      |    "tasksStatus": []
      |  }
      | """.stripMargin.parseJson)
    an[NoSuchElementException] should be thrownBy connectorDescription.className
  }

  @Test
  def parsePropGroups(): Unit = {
    val creationRequest = ConnectorApi.CONNECTOR_CREATION_FORMAT.read(s"""
      |  {
      |    "$CONNECTOR_CLASS_KEY": "${CommonUtils.randomString()}",
      |    "columns": [
      |      {
      |       "order": 1,
      |       "name": "abc",
      |       "newName": "ccc",
      |       "dataType": "STRING"
      |      }
      |    ]
      |  }
      | """.stripMargin.parseJson)
    val column = PropGroups.ofJson(creationRequest.settings("columns").toString()).toColumns.get(0)
    column.order() shouldBe 1
    column.name() shouldBe "abc"
    column.newName() shouldBe "ccc"
    column.dataType().name() shouldBe "STRING"
  }

  @Test
  def parseStaleConfigs(): Unit = {
    val creationRequest = ConnectorApi.CONNECTOR_CREATION_FORMAT.read(s"""
          |  {
          |    "name": "ftpsource",
          |    "$CONNECTOR_CLASS_KEY": "${CommonUtils.randomString()}",
          |    "$COLUMNS_KEY": [
          |    {
          |      "name": "col1",
          |      "newName": "col1",
          |      "dataType": "STRING",
          |      "order": 1
          |    },
          |    {
          |      "name": "col2",
          |      "newName": "col2",
          |      "dataType": "STRING",
          |      "order": 2
          |    },
          |    {
          |      "name": "col3",
          |      "newName": "col3",
          |      "dataType": "STRING",
          |      "order": 3
          |    }
          |  ],
          |  "$CONNECTOR_CLASS_KEY": "com.island.ohara.connector.ftp.FtpSource",
          |  "numberOfTasks": 1,
          |  "configs": {
          |    "ftp.input.folder": "/demo_folder/input",
          |    "ftp.completed.folder": "/demo_folder/complete",
          |    "ftp.error.folder": "/demo_folder/error",
          |    "ftp.encode": "UTF-8",
          |    "ftp.hostname": "10.2.0.28",
          |    "ftp.port": "21",
          |    "ftp.user.name": "ohara",
          |    "ftp.user.password": "island123",
          |    "currTask": "1"
          |  }
          |}
          |     """.stripMargin.parseJson)
    // the deprecated APIs should not be supported now!!!
    creationRequest.settings.contains("ftp.input.folder") shouldBe false
    creationRequest.settings.contains("ftp.completed.folder") shouldBe false
    creationRequest.settings.contains("ftp.error.folder") shouldBe false
    creationRequest.settings.contains("ftp.encode") shouldBe false
    creationRequest.settings.contains("ftp.hostname") shouldBe false
    creationRequest.settings.contains("ftp.port") shouldBe false
  }

  @Test
  def ignoreClassNameOnCreation(): Unit = intercept[DeserializationException] {
    ConnectorApi.access.hostname(CommonUtils.randomString()).port(CommonUtils.availablePort()).request.creation
  }.getMessage should include(CONNECTOR_CLASS_KEY)

  @Test
  def ignoreNameOnCreation(): Unit = ConnectorApi.access
    .hostname(CommonUtils.randomString())
    .port(CommonUtils.availablePort())
    .request
    .className(CommonUtils.randomString())
    .creation
    .name
    .length should not be 0

  @Test
  def ignoreNameOnUpdate(): Unit = an[NoSuchElementException] should be thrownBy ConnectorApi.access
    .hostname(CommonUtils.randomString())
    .port(CommonUtils.availablePort())
    .request
    .update()

  @Test
  def emptyGroup(): Unit = an[IllegalArgumentException] should be thrownBy ConnectorApi.access.request.group("")

  @Test
  def nullGroup(): Unit = an[NullPointerException] should be thrownBy ConnectorApi.access.request.group(null)

  @Test
  def emptyName(): Unit = an[IllegalArgumentException] should be thrownBy ConnectorApi.access.request.name("")

  @Test
  def nullName(): Unit = an[NullPointerException] should be thrownBy ConnectorApi.access.request.name(null)

  @Test
  def emptyClassName(): Unit =
    an[IllegalArgumentException] should be thrownBy ConnectorApi.access.request.className("")

  @Test
  def nullClassName(): Unit =
    an[NullPointerException] should be thrownBy ConnectorApi.access.request.className(null)

  @Test
  def emptyColumns(): Unit = ConnectorApi.access.request.columns(Seq.empty)

  @Test
  def nullColumns(): Unit = an[NullPointerException] should be thrownBy ConnectorApi.access.request.columns(null)

  @Test
  def nullWorkerClusterKey(): Unit =
    an[NullPointerException] should be thrownBy ConnectorApi.access.request.workerClusterKey(null)

  @Test
  def emptyTopicKeys(): Unit = ConnectorApi.access.request.topicKeys(Set.empty)

  @Test
  def nullTopicKeys(): Unit =
    an[NullPointerException] should be thrownBy ConnectorApi.access.request.topicKeys(null)

  @Test
  def emptySettings(): Unit =
    an[IllegalArgumentException] should be thrownBy ConnectorApi.access.request.settings(Map.empty)

  @Test
  def nullSettings(): Unit = an[NullPointerException] should be thrownBy ConnectorApi.access.request.settings(null)

  @Test
  def testCreation(): Unit = {
    val name = CommonUtils.randomString(10)
    val className = CommonUtils.randomString(10)
    val topicKeys = Set(TopicKey.of(CommonUtils.randomString(10), CommonUtils.randomString(10)))
    val map = Map(
      CommonUtils.randomString(10) -> JsString(CommonUtils.randomString(10)),
      CommonUtils.randomString(10) -> JsString(CommonUtils.randomString(10)),
      CommonUtils.randomString(10) -> JsString(CommonUtils.randomString(10)),
    )
    val creation =
      ConnectorApi.access.request.name(name).className(className).topicKeys(topicKeys).settings(map).creation
    creation.name shouldBe name
    creation.className shouldBe className
    creation.topicKeys shouldBe topicKeys
    map.foreach {
      case (k, v) => creation.plain(k) shouldBe v.convertTo[String]
    }
  }

  @Test
  def testDefaultNumberOfTasks(): Unit =
    ConnectorApi.CONNECTOR_CREATION_FORMAT.read(s"""
      |  {
      |    "name": "ftpsource",
      |    "$CONNECTOR_CLASS_KEY": "com.island.ohara.connector.ftp.FtpSource"
      |  }
      |     """.stripMargin.parseJson).numberOfTasks shouldBe 1

  @Test
  def parseColumn(): Unit = {
    val name = CommonUtils.randomString()
    val newName = CommonUtils.randomString()
    val dataType = DataType.BOOLEAN
    val order = 1
    val creation = ConnectorApi.CONNECTOR_CREATION_FORMAT.read(s"""
                                            |  {
                                            |    "$CONNECTOR_CLASS_KEY": "${CommonUtils.randomString()}",
                                            |    "$COLUMNS_KEY": [
                                            |      {
                                            |        "name": "$name",
                                            |        "newName": "$newName",
                                            |        "dataType": "${dataType.name}",
                                            |        "order": $order
                                            |      }
                                            |    ]
                                            |  }
                                            |""".stripMargin.parseJson)
    creation.columns.size shouldBe 1
    val column = creation.columns.head
    column.name shouldBe name
    column.newName shouldBe newName
    column.dataType shouldBe dataType
    column.order shouldBe order

    val creation2 = ConnectorApi.CONNECTOR_CREATION_FORMAT.read(s"""
                                                                       |  {
                                                                       |    "$CONNECTOR_CLASS_KEY": "com.island.ohara.connector.ftp.FtpSource",
                                                                       |    "$COLUMNS_KEY": [
                                                                       |      {
                                                                       |        "name": "$name",
                                                                       |        "dataType": "${dataType.name}",
                                                                       |        "order": $order
                                                                       |      }
                                                                       |    ]
                                                                       |  }
                                                                       |""".stripMargin.parseJson)
    creation2.columns.size shouldBe 1
    val column2 = creation2.columns.head
    column2.name shouldBe name
    column2.newName shouldBe name
    column2.dataType shouldBe dataType
    column2.order shouldBe order
  }

  @Test
  def emptyNameForCreatingColumn(): Unit =
    an[DeserializationException] should be thrownBy ConnectorApi.CONNECTOR_CREATION_FORMAT.read(s"""
                                                                                                   |  {
                                                                                                   |    "$CONNECTOR_CLASS_KEY": "com.island.ohara.connector.ftp.FtpSource",
                                                                                                   |    "$COLUMNS_KEY": [
                                                                                                   |      {
                                                                                                   |        "name": "",
                                                                                                   |        "dataType": "Boolean",
                                                                                                   |        "order": 1
                                                                                                   |      }
                                                                                                   |    ]
                                                                                                   |  }
                                                                                                        |""".stripMargin.parseJson)

  @Test
  def emptyNewNameForCreatingColumn(): Unit =
    an[DeserializationException] should be thrownBy ConnectorApi.CONNECTOR_CREATION_FORMAT.read(s"""
                                                                                                   |  {
                                                                                                   |    "$CONNECTOR_CLASS_KEY": "com.island.ohara.connector.ftp.FtpSource",
                                                                                                   |    "$COLUMNS_KEY": [
                                                                                                   |      {
                                                                                                   |        "name": "AA",
                                                                                                   |        "newName": "",
                                                                                                   |        "dataType": "Boolean",
                                                                                                   |        "order": 1
                                                                                                   |      }
                                                                                                   |    ]
                                                                                                   |  }
                                                                                                        |""".stripMargin.parseJson)

  @Test
  def negativeOrderForCreatingColumn(): Unit =
    an[DeserializationException] should be thrownBy ConnectorApi.CONNECTOR_CREATION_FORMAT.read(s"""
                                                        |  {
                                                        |    "$CONNECTOR_CLASS_KEY": "com.island.ohara.connector.ftp.FtpSource",
                                                        |    "$COLUMNS_KEY": [
                                                        |      {
                                                        |        "name": "AA",
                                                        |        "newName": "cc",
                                                        |        "dataType": "Boolean",
                                                        |        "order": -1
                                                        |      }
                                                        |    ]
                                                        |  }
                                                        |""".stripMargin.parseJson)

  @Test
  def duplicateOrderForCreatingColumns(): Unit =
    an[DeserializationException] should be thrownBy ConnectorApi.CONNECTOR_CREATION_FORMAT.read(s"""
                                                                                                   |  {
                                                                                                   |    "$CONNECTOR_CLASS_KEY": "com.island.ohara.connector.ftp.FtpSource",
                                                                                                   |    "$COLUMNS_KEY": [
                                                                                                   |      {
                                                                                                   |        "name": "AA",
                                                                                                   |        "newName": "",
                                                                                                   |        "dataType": "Boolean",
                                                                                                   |        "order": 1
                                                                                                   |      },
                                                                                                   |      {
                                                                                                   |        "name": "AA",
                                                                                                   |        "newName": "",
                                                                                                   |        "dataType": "Boolean",
                                                                                                   |        "order": 1
                                                                                                   |      }
                                                                                                   |    ]
                                                                                                   |  }
                                                                                                   |""".stripMargin.parseJson)

  @Test
  def emptyNameForUpdatingColumn(): Unit =
    an[DeserializationException] should be thrownBy ConnectorApi.CONNECTOR_UPDATING_FORMAT.read(s"""
                                                                                                        |  {
                                                                                                        |    "$CONNECTOR_CLASS_KEY": "com.island.ohara.connector.ftp.FtpSource",
                                                                                                        |    "$COLUMNS_KEY": [
                                                                                                        |      {
                                                                                                        |        "name": "",
                                                                                                        |         "dataType": "Boolean",
                                                                                                        |         "order": 1
                                                                                                        |       }
                                                                                                        |    ]
                                                                                                        |  }
                                                                                                        |""".stripMargin.parseJson)

  @Test
  def emptyNewNameForUpdatingColumn(): Unit =
    an[DeserializationException] should be thrownBy ConnectorApi.CONNECTOR_UPDATING_FORMAT.read(s"""
                                                                                                   |  {
                                                                                                   |    "$CONNECTOR_CLASS_KEY": "com.island.ohara.connector.ftp.FtpSource",
                                                                                                   |    "$COLUMNS_KEY": [
                                                                                                   |      {
                                                                                                   |        "name": "AA",
                                                                                                   |        "newName": "",
                                                                                                   |        "dataType": "Boolean",
                                                                                                   |        "order": 1
                                                                                                   |      }
                                                                                                   |    ]
                                                                                                   |  }
                                                                                                   |""".stripMargin.parseJson)

  @Test
  def negativeOrderForUpdatingColumn(): Unit =
    an[DeserializationException] should be thrownBy ConnectorApi.CONNECTOR_UPDATING_FORMAT.read(s"""
                                                                                                 |  {
                                                                                                 |    "$CONNECTOR_CLASS_KEY": "com.island.ohara.connector.ftp.FtpSource",
                                                                                                 |    "$COLUMNS_KEY": [
                                                                                                 |      {
                                                                                                 |        "name": "AA",
                                                                                                 |        "newName": "cc",
                                                                                                 |        "dataType": "Boolean",
                                                                                                 |        "order": -1
                                                                                                 |      }
                                                                                                 |    ]
                                                                                                 |  }
                                                                                                 |""".stripMargin.parseJson)

  @Test
  def duplicateOrderForUpdatingColumns(): Unit =
    an[DeserializationException] should be thrownBy ConnectorApi.CONNECTOR_UPDATING_FORMAT.read(s"""
                                                                                                   |  {
                                                                                                   |    "$CONNECTOR_CLASS_KEY": "com.island.ohara.connector.ftp.FtpSource",
                                                                                                   |    "$COLUMNS_KEY": [
                                                                                                   |      {
                                                                                                   |        "name": "AA",
                                                                                                   |        "newName": "",
                                                                                                   |        "dataType": "Boolean",
                                                                                                   |        "order": 1
                                                                                                   |      },
                                                                                                   |      {
                                                                                                   |        "name": "AA",
                                                                                                   |        "newName": "",
                                                                                                   |        "dataType": "Boolean",
                                                                                                   |        "order": 1
                                                                                                   |      }
                                                                                                   |    ]
                                                                                                   |  }
                                                                                                   |""".stripMargin.parseJson)

  @Test
  def nullTags(): Unit = an[NullPointerException] should be thrownBy ConnectorApi.access.request.tags(null)

  @Test
  def emptyTags(): Unit = ConnectorApi.access.request.tags(Map.empty)
  @Test
  def parseTags(): Unit =
    ConnectorApi.CONNECTOR_CREATION_FORMAT.read(s"""
      |  {
      |    "$CONNECTOR_CLASS_KEY": "com.island.ohara.connector.ftp.FtpSource",
      |    "tags": {
      |      "a": "bb",
      |      "b": 123
      |    }
      |  }
      |     """.stripMargin.parseJson).tags shouldBe Map(
      "a" -> JsString("bb"),
      "b" -> JsNumber(123)
    )

  @Test
  def parseNullTags(): Unit =
    ConnectorApi.CONNECTOR_CREATION_FORMAT.read(s"""
      |  {
      |    "$CONNECTOR_CLASS_KEY": "com.island.ohara.connector.ftp.FtpSource"
      |  }
      |     """.stripMargin.parseJson).tags shouldBe Map.empty

  @Test
  def parseConnectorKey(): Unit =
    an[DeserializationException] should be thrownBy ConnectorApi.CONNECTOR_CREATION_FORMAT.read(s"""
       |  {
       |    "$CONNECTOR_CLASS_KEY": "com.island.ohara.connector.ftp.FtpSource",
       |    "$CONNECTOR_KEY_KEY": {
       |      "group": "g",
       |      "name": "n"
       |    }
       |  }
       |     """.stripMargin.parseJson)

  @Test
  def testCustomGroup(): Unit = ConnectorApi.access
    .hostname(CommonUtils.randomString())
    .port(CommonUtils.availablePort())
    .request
    .group("abc")
    .className(CommonUtils.randomString())
    .creation
    .group shouldBe "abc"

  @Test
  def testDefaultGroup(): Unit = ConnectorApi.access
    .hostname(CommonUtils.randomString())
    .port(CommonUtils.availablePort())
    .request
    .className(CommonUtils.randomString())
    .creation
    .group shouldBe GROUP_DEFAULT

  @Test
  def rejectTopicKeyword(): Unit = intercept[DeserializationException] {
    CONNECTOR_CREATION_FORMAT.read(s"""
                                      |{
                                      |  "connector.class": "aa",
                                      |  "topics": []
                                      |}
      """.stripMargin.parseJson)
  }.getMessage should include("illegal word")

  @Test
  def testStaleNameAndGroup(): Unit = {
    val name = CommonUtils.randomString(5)
    val group = CommonUtils.randomString(5)
    val connectorInfo = ConnectorDescription(
      settings =
        ConnectorApi.access.request.group(group).name(name).className(CommonUtils.randomString()).creation.settings,
      status = None,
      tasksStatus = Seq.empty,
      metrics = Metrics.EMPTY,
      lastModified = CommonUtils.current()
    )

    val js = CONNECTOR_DESCRIPTION_FORMAT.write(connectorInfo)
    js.asJsObject.fields(NAME_KEY).convertTo[String] shouldBe name
    js.asJsObject.fields(GROUP_KEY).convertTo[String] shouldBe group
  }
}
