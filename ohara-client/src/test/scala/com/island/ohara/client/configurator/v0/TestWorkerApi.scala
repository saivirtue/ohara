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

import com.island.ohara.client.configurator.v0.WorkerApi._
import com.island.ohara.common.rule.SmallTest
import com.island.ohara.common.util.CommonUtils
import org.junit.Test
import org.scalatest.Matchers
import spray.json.DeserializationException
import spray.json._

class TestWorkerApi extends SmallTest with Matchers {

  @Test
  def testResponseEquals(): Unit = {
    val response = WorkerClusterInfo(
      name = CommonUtils.randomString(),
      imageName = CommonUtils.randomString(),
      brokerClusterName = CommonUtils.randomString(),
      clientPort = 10,
      jmxPort = 10,
      groupId = CommonUtils.randomString(),
      statusTopicName = CommonUtils.randomString(),
      statusTopicPartitions = 10,
      statusTopicReplications = 10,
      configTopicName = CommonUtils.randomString(),
      configTopicPartitions = 10,
      configTopicReplications = 10,
      offsetTopicName = CommonUtils.randomString(),
      offsetTopicPartitions = 10,
      offsetTopicReplications = 10,
      jarInfos = Seq.empty,
      connectors = Seq.empty,
      nodeNames = Set.empty,
      deadNodes = Set.empty,
      state = None,
      error = None,
      tags = Map.empty,
      lastModified = CommonUtils.current()
    )

    response shouldBe WORKER_CLUSTER_INFO_JSON_FORMAT.read(WORKER_CLUSTER_INFO_JSON_FORMAT.write(response))
  }

  @Test
  def testCloneNodeNames(): Unit = {
    val newNodeNames = Set(CommonUtils.randomString())
    val workerClusterInfo = WorkerClusterInfo(
      name = CommonUtils.randomString(),
      imageName = CommonUtils.randomString(),
      brokerClusterName = CommonUtils.randomString(),
      clientPort = 10,
      jmxPort = 10,
      groupId = CommonUtils.randomString(),
      statusTopicName = CommonUtils.randomString(),
      statusTopicPartitions = 10,
      statusTopicReplications = 10,
      configTopicName = CommonUtils.randomString(),
      configTopicPartitions = 10,
      configTopicReplications = 10,
      offsetTopicName = CommonUtils.randomString(),
      offsetTopicPartitions = 10,
      offsetTopicReplications = 10,
      jarInfos = Seq.empty,
      connectors = Seq.empty,
      nodeNames = Set.empty,
      deadNodes = Set.empty,
      state = None,
      error = None,
      tags = Map.empty,
      lastModified = CommonUtils.current()
    )
    workerClusterInfo.clone(newNodeNames).nodeNames shouldBe newNodeNames
  }

  @Test
  def ignoreNameOnCreation(): Unit = WorkerApi.access
    .hostname(CommonUtils.randomString())
    .port(CommonUtils.availablePort())
    .request
    .nodeName(CommonUtils.randomString(10))
    .creation
    .name
    .length should not be 0

  @Test
  def testTags(): Unit = WorkerApi.access
    .hostname(CommonUtils.randomString())
    .port(CommonUtils.availablePort())
    .request
    .nodeName(CommonUtils.randomString(10))
    .tags(Map("a" -> JsNumber(1), "b" -> JsString("2")))
    .creation
    .tags
    .size shouldBe 2

  @Test
  def ignoreNodeNamesOnCreation(): Unit = an[IllegalArgumentException] should be thrownBy WorkerApi.access
    .hostname(CommonUtils.randomString())
    .port(CommonUtils.availablePort())
    .request
    .name(CommonUtils.randomString())
    .creation

  @Test
  def nullName(): Unit = an[NullPointerException] should be thrownBy WorkerApi.access
    .hostname(CommonUtils.randomString())
    .port(CommonUtils.availablePort())
    .request
    .name(null)

  @Test
  def emptyName(): Unit = an[IllegalArgumentException] should be thrownBy WorkerApi.access
    .hostname(CommonUtils.randomString())
    .port(CommonUtils.availablePort())
    .request
    .name("")

  @Test
  def nullBrokerClusterName(): Unit = an[NullPointerException] should be thrownBy WorkerApi.access
    .hostname(CommonUtils.randomString())
    .port(CommonUtils.availablePort())
    .request
    .brokerClusterName(null)

  @Test
  def emptyBrokerClusterName(): Unit = an[IllegalArgumentException] should be thrownBy WorkerApi.access
    .hostname(CommonUtils.randomString())
    .port(CommonUtils.availablePort())
    .request
    .brokerClusterName("")

  @Test
  def nullImageName(): Unit = an[NullPointerException] should be thrownBy WorkerApi.access
    .hostname(CommonUtils.randomString())
    .port(CommonUtils.availablePort())
    .request
    .imageName(null)

  @Test
  def emptyImageName(): Unit = an[IllegalArgumentException] should be thrownBy WorkerApi.access
    .hostname(CommonUtils.randomString())
    .port(CommonUtils.availablePort())
    .request
    .imageName("")

  @Test
  def nullNodeNames(): Unit = an[NullPointerException] should be thrownBy WorkerApi.access
    .hostname(CommonUtils.randomString())
    .port(CommonUtils.availablePort())
    .request
    .nodeNames(null)

  @Test
  def emptyNodeNames(): Unit = an[IllegalArgumentException] should be thrownBy WorkerApi.access
    .hostname(CommonUtils.randomString())
    .port(CommonUtils.availablePort())
    .request
    .nodeNames(Set.empty)

  @Test
  def negativeClientPort(): Unit = an[IllegalArgumentException] should be thrownBy WorkerApi.access
    .hostname(CommonUtils.randomString())
    .port(CommonUtils.availablePort())
    .request
    .clientPort(-1)

  @Test
  def negativeJmxPort(): Unit = an[IllegalArgumentException] should be thrownBy WorkerApi.access
    .hostname(CommonUtils.randomString())
    .port(CommonUtils.availablePort())
    .request
    .jmxPort(-1)

  @Test
  def nullConfigTopicName(): Unit = an[NullPointerException] should be thrownBy WorkerApi.access
    .hostname(CommonUtils.randomString())
    .port(CommonUtils.availablePort())
    .request
    .configTopicName(null)

  @Test
  def emptyConfigTopicName(): Unit = an[IllegalArgumentException] should be thrownBy WorkerApi.access
    .hostname(CommonUtils.randomString())
    .port(CommonUtils.availablePort())
    .request
    .configTopicName("")

  @Test
  def negativeNumberOfConfigTopicReplication(): Unit = an[IllegalArgumentException] should be thrownBy WorkerApi.access
    .hostname(CommonUtils.randomString())
    .port(CommonUtils.availablePort())
    .request
    .configTopicReplications(-1)

  @Test
  def nullOffsetTopicName(): Unit = an[NullPointerException] should be thrownBy WorkerApi.access
    .hostname(CommonUtils.randomString())
    .port(CommonUtils.availablePort())
    .request
    .offsetTopicName(null)

  @Test
  def emptyOffsetTopicName(): Unit = an[IllegalArgumentException] should be thrownBy WorkerApi.access
    .hostname(CommonUtils.randomString())
    .port(CommonUtils.availablePort())
    .request
    .offsetTopicName("")

  @Test
  def negativeNumberOfOffsetTopicPartitions(): Unit = an[IllegalArgumentException] should be thrownBy WorkerApi.access
    .hostname(CommonUtils.randomString())
    .port(CommonUtils.availablePort())
    .request
    .offsetTopicPartitions(-1)

  @Test
  def negativeNumberOfOffsetTopicReplication(): Unit = an[IllegalArgumentException] should be thrownBy WorkerApi.access
    .hostname(CommonUtils.randomString())
    .port(CommonUtils.availablePort())
    .request
    .offsetTopicReplications(-1)

  @Test
  def nullStatusTopicName(): Unit = an[NullPointerException] should be thrownBy WorkerApi.access
    .hostname(CommonUtils.randomString())
    .port(CommonUtils.availablePort())
    .request
    .statusTopicName(null)

  @Test
  def emptyStatusTopicName(): Unit = an[IllegalArgumentException] should be thrownBy WorkerApi.access
    .hostname(CommonUtils.randomString())
    .port(CommonUtils.availablePort())
    .request
    .statusTopicName("")

  @Test
  def negativeNumberOfStatusTopicPartitions(): Unit = an[IllegalArgumentException] should be thrownBy WorkerApi.access
    .hostname(CommonUtils.randomString())
    .port(CommonUtils.availablePort())
    .request
    .statusTopicPartitions(-1)

  @Test
  def negativeNumberOfStatusTopicReplication(): Unit = an[IllegalArgumentException] should be thrownBy WorkerApi.access
    .hostname(CommonUtils.randomString())
    .port(CommonUtils.availablePort())
    .request
    .statusTopicReplications(-1)

  @Test
  def testCreation(): Unit = {
    val name = CommonUtils.randomString()
    val imageName = CommonUtils.randomString()
    val clientPort = CommonUtils.availablePort()
    val jmxPort = CommonUtils.availablePort()
    val brokerClusterName = CommonUtils.randomString()
    val configTopicName = CommonUtils.randomString(10)
    val configTopicReplications: Short = 2
    val offsetTopicName = CommonUtils.randomString(10)
    val offsetTopicPartitions: Int = 2
    val offsetTopicReplications: Short = 2
    val statusTopicName = CommonUtils.randomString(10)
    val statusTopicPartitions: Int = 2
    val statusTopicReplications: Short = 2
    val nodeName = CommonUtils.randomString()
    val creation = WorkerApi.access
      .hostname(CommonUtils.randomString())
      .port(CommonUtils.availablePort())
      .request
      .name(name)
      .brokerClusterName(brokerClusterName)
      .configTopicName(configTopicName)
      .configTopicReplications(configTopicReplications)
      .offsetTopicName(offsetTopicName)
      .offsetTopicPartitions(offsetTopicPartitions)
      .offsetTopicReplications(offsetTopicReplications)
      .statusTopicName(statusTopicName)
      .statusTopicPartitions(statusTopicPartitions)
      .statusTopicReplications(statusTopicReplications)
      .imageName(imageName)
      .clientPort(clientPort)
      .jmxPort(jmxPort)
      .nodeName(nodeName)
      .creation
    creation.name shouldBe name
    creation.imageName shouldBe imageName
    creation.clientPort shouldBe clientPort
    creation.jmxPort shouldBe jmxPort
    creation.brokerClusterName.get shouldBe brokerClusterName
    creation.configTopicName shouldBe configTopicName
    creation.configTopicReplications shouldBe configTopicReplications
    creation.offsetTopicName shouldBe offsetTopicName
    creation.offsetTopicPartitions shouldBe offsetTopicPartitions
    creation.offsetTopicReplications shouldBe offsetTopicReplications
    creation.statusTopicName shouldBe statusTopicName
    creation.statusTopicPartitions shouldBe statusTopicPartitions
    creation.statusTopicReplications shouldBe statusTopicReplications
    creation.nodeNames.head shouldBe nodeName
  }

  @Test
  def parseCreation(): Unit = {
    val nodeName = CommonUtils.randomString()
    val creation = WorkerApi.WORKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "nodeNames": ["$nodeName"]
      |  }
      |  """.stripMargin.parseJson)
    creation.name.length shouldBe 10
    creation.imageName shouldBe WorkerApi.IMAGE_NAME_DEFAULT
    creation.brokerClusterName shouldBe None
    creation.configTopicReplications shouldBe 1
    creation.offsetTopicReplications shouldBe 1
    creation.offsetTopicPartitions shouldBe 1
    creation.statusTopicReplications shouldBe 1
    creation.statusTopicPartitions shouldBe 1
    creation.nodeNames.size shouldBe 1
    creation.nodeNames.head shouldBe nodeName
    creation.jarKeys.size shouldBe 0

    val name = CommonUtils.randomString(10)
    val creation2 = WorkerApi.WORKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "name": "$name",
      |    "nodeNames": ["$nodeName"]
      |  }
      |  """.stripMargin.parseJson)
    creation2.name shouldBe name
    creation2.imageName shouldBe WorkerApi.IMAGE_NAME_DEFAULT
    creation2.brokerClusterName shouldBe None
    creation2.configTopicReplications shouldBe 1
    creation2.offsetTopicReplications shouldBe 1
    creation2.offsetTopicPartitions shouldBe 1
    creation2.statusTopicReplications shouldBe 1
    creation2.statusTopicPartitions shouldBe 1
    creation2.nodeNames.size shouldBe 1
    creation2.nodeNames.head shouldBe nodeName
    creation2.jarKeys.size shouldBe 0
  }

  @Test
  def parseEmptyNodeNames(): Unit =
    an[DeserializationException] should be thrownBy WorkerApi.WORKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "name": "asdasd",
      |    "nodeNames": []
      |  }
      |  """.stripMargin.parseJson)
  @Test
  def parseNodeNamesOnUpdate(): Unit = {
    val thrown1 = the[DeserializationException] thrownBy WorkerApi.WORKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "nodeNames": ""
      |  }
      |  """.stripMargin.parseJson)
    thrown1.getMessage should include("the value of \"nodeNames\" can't be empty string")
  }

  @Test
  def parseZeroClientPort(): Unit =
    an[DeserializationException] should be thrownBy WorkerApi.WORKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "name": "name",
      |    "clientPort": 0,
      |    "nodeNames": ["n"]
      |  }
      |  """.stripMargin.parseJson)

  @Test
  def parseNegativeClientPort(): Unit =
    an[DeserializationException] should be thrownBy WorkerApi.WORKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "name": "name",
      |    "clientPort": -1,
      |    "nodeNames": ["n"]
      |  }
      |  """.stripMargin.parseJson)

  @Test
  def parseLargeClientPort(): Unit =
    an[DeserializationException] should be thrownBy WorkerApi.WORKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "name": "name",
      |    "clientPort": 999999,
      |    "nodeNames": ["n"]
      |  }
      |  """.stripMargin.parseJson)

  @Test
  def parseClientPortOnUpdate(): Unit = {
    val thrown1 = the[DeserializationException] thrownBy WorkerApi.WORKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "clientPort": 0
      |  }
      |  """.stripMargin.parseJson)
    thrown1.getMessage should include("the connection port must be [1024, 65535)")

    val thrown2 = the[DeserializationException] thrownBy WorkerApi.WORKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "clientPort": -9
      |  }
      |  """.stripMargin.parseJson)
    thrown2.getMessage should include("\"clientPort\" MUST be bigger than or equal to zero")

    val thrown3 = the[DeserializationException] thrownBy WorkerApi.WORKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "clientPort": 99999
      |  }
      |  """.stripMargin.parseJson)
    thrown3.getMessage should include("the connection port must be [1024, 65535), but actual port is \"99999\"")
  }

  @Test
  def parseZeroJmxPort(): Unit =
    an[DeserializationException] should be thrownBy WorkerApi.WORKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "name": "name",
      |    "jmxPort": 0,
      |    "nodeNames": ["n"]
      |  }
      |  """.stripMargin.parseJson)

  @Test
  def parseNegativeJmxPort(): Unit =
    an[DeserializationException] should be thrownBy WorkerApi.WORKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "name": "name",
      |    "jmxPort": -1,
      |    "nodeNames": ["n"]
      |  }
      |  """.stripMargin.parseJson)

  @Test
  def parseLargeJmxPort(): Unit =
    an[DeserializationException] should be thrownBy WorkerApi.WORKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "name": "name",
      |    "jmxPort": 999999,
      |    "nodeNames": ["n"]
      |  }
      |  """.stripMargin.parseJson)

  @Test
  def parseJmxPortOnUpdate(): Unit = {
    val thrown1 = the[DeserializationException] thrownBy WorkerApi.WORKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "jmxPort": 0
      |  }
      |  """.stripMargin.parseJson)
    thrown1.getMessage should include("the connection port must be [1024, 65535)")

    val thrown2 = the[DeserializationException] thrownBy WorkerApi.WORKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "jmxPort": -9
      |  }
      |  """.stripMargin.parseJson)
    thrown2.getMessage should include("\"jmxPort\" MUST be bigger than or equal to zero")

    val thrown3 = the[DeserializationException] thrownBy WorkerApi.WORKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "jmxPort": 99999
      |  }
      |  """.stripMargin.parseJson)
    thrown3.getMessage should include("the connection port must be [1024, 65535), but actual port is \"99999\"")
  }

}
