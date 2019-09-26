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

import com.island.ohara.client.configurator.v0.BrokerApi.BrokerClusterInfo
import com.island.ohara.common.rule.OharaTest
import com.island.ohara.common.setting.ObjectKey
import com.island.ohara.common.util.CommonUtils
import org.junit.Test
import org.scalatest.Matchers
import spray.json.DefaultJsonProtocol._
import spray.json.{DeserializationException, _}

class TestBrokerApi extends OharaTest with Matchers {

  private[this] final val access =
    BrokerApi.access.hostname(CommonUtils.randomString(5)).port(CommonUtils.availablePort()).request

  @Test
  def testClone(): Unit = {
    val nodeNames = Set(CommonUtils.randomString())
    val brokerClusterInfo = BrokerClusterInfo(
      settings = access.nodeNames(Set(CommonUtils.randomString())).creation.settings,
      aliveNodes = Set.empty,
      state = None,
      error = None,
      lastModified = CommonUtils.current(),
      topicSettingDefinitions = Seq.empty
    )
    brokerClusterInfo.newNodeNames(nodeNames).nodeNames shouldBe nodeNames
  }

  @Test
  def ignoreNameOnCreation(): Unit = access.nodeName(CommonUtils.randomString(10)).creation.name.length should not be 0

  @Test
  def testTags(): Unit = access
    .nodeName(CommonUtils.randomString(10))
    .tags(Map("a" -> JsNumber(1), "b" -> JsString("2")))
    .creation
    .tags
    .size shouldBe 2

  @Test
  def ignoreNodeNamesOnCreation(): Unit =
    an[DeserializationException] should be thrownBy access.name(CommonUtils.randomString(10)).creation

  @Test
  def nullName(): Unit = an[NullPointerException] should be thrownBy access.name(null)

  @Test
  def emptyName(): Unit = an[IllegalArgumentException] should be thrownBy access.name("")

  @Test
  def nullGroup(): Unit = an[NullPointerException] should be thrownBy access.group(null)

  @Test
  def emptyGroup(): Unit = an[IllegalArgumentException] should be thrownBy access.group("")

  @Test
  def nullZookeeperClusterKey(): Unit = an[NullPointerException] should be thrownBy access.zookeeperClusterKey(null)

  @Test
  def nullImageName(): Unit = an[NullPointerException] should be thrownBy access.imageName(null)

  @Test
  def emptyImageName(): Unit = an[IllegalArgumentException] should be thrownBy access.imageName("")

  @Test
  def nullNodeNames(): Unit = an[NullPointerException] should be thrownBy access.nodeNames(null)

  @Test
  def emptyNodeNames(): Unit = an[IllegalArgumentException] should be thrownBy access.nodeNames(Set.empty)

  @Test
  def negativeClientPort(): Unit = an[IllegalArgumentException] should be thrownBy access.clientPort(-1)

  @Test
  def negativeJmxPort(): Unit = an[IllegalArgumentException] should be thrownBy access.jmxPort(-1)

  @Test
  def negativeExporterPort(): Unit = an[IllegalArgumentException] should be thrownBy access.exporterPort(-1)

  @Test
  def testCreation(): Unit = {
    val name = CommonUtils.randomString(10)
    val group = CommonUtils.randomString(10)
    val imageName = CommonUtils.randomString()
    val clientPort = CommonUtils.availablePort()
    val jmxPort = CommonUtils.availablePort()
    val exporterPort = CommonUtils.availablePort()
    val zkKey = ObjectKey.of(CommonUtils.randomString(), CommonUtils.randomString())
    val nodeName = CommonUtils.randomString()
    val creation = access
      .name(name)
      .group(group)
      .zookeeperClusterKey(zkKey)
      .imageName(imageName)
      .clientPort(clientPort)
      .jmxPort(jmxPort)
      .exporterPort(exporterPort)
      .nodeName(nodeName)
      .creation
    creation.name shouldBe name
    creation.group shouldBe group
    creation.imageName shouldBe imageName
    creation.clientPort shouldBe clientPort
    creation.jmxPort shouldBe jmxPort
    creation.exporterPort shouldBe exporterPort
    creation.zookeeperClusterKey.get shouldBe zkKey
    creation.nodeNames.head shouldBe nodeName
  }

  @Test
  def testExtraSettingInCreation(): Unit = {
    val name = CommonUtils.randomString(10)
    val name2 = JsString(CommonUtils.randomString(10))
    val creation = access.name(name).nodeNames(Set("n1")).settings(Map("name" -> name2)).creation

    // settings() has higher priority than name()
    creation.name shouldBe name2.value
  }

  @Test
  def parseCreation(): Unit = {
    val nodeName = CommonUtils.randomString()
    val creation = BrokerApi.BROKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "nodeNames": ["$nodeName"]
      |  }
      """.stripMargin.parseJson)
    creation.group shouldBe GROUP_DEFAULT
    creation.name.length shouldBe LIMIT_OF_KEY_LENGTH / 2
    creation.imageName shouldBe BrokerApi.IMAGE_NAME_DEFAULT
    creation.zookeeperClusterKey shouldBe None
    creation.nodeNames.size shouldBe 1
    creation.nodeNames.head shouldBe nodeName
    creation.clientPort should not be 0
    creation.jmxPort should not be 0
    creation.exporterPort should not be 0
    creation.ports.size shouldBe 3

    val name = CommonUtils.randomString(10)
    val group = CommonUtils.randomString(10)
    val zookeeperClusterName = CommonUtils.randomString()
    val clientPort = CommonUtils.availablePort()
    val exporterPort = CommonUtils.availablePort()
    val jmxPort = CommonUtils.availablePort()
    val creation2 = BrokerApi.BROKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "name": "$name",
      |    "group": "$group",
      |    "clientPort": $clientPort,
      |    "exporterPort": $exporterPort,
      |    "jmxPort": $jmxPort,
      |    "zookeeperClusterName": "$zookeeperClusterName",
      |    "nodeNames": ["$nodeName"]
      |  }
      """.stripMargin.parseJson)
    // group is support in create cluster
    creation2.group shouldBe group
    creation2.name shouldBe name
    creation2.imageName shouldBe BrokerApi.IMAGE_NAME_DEFAULT
    creation2.nodeNames.size shouldBe 1
    creation2.nodeNames.head shouldBe nodeName
    creation2.zookeeperClusterKey.get.name() shouldBe zookeeperClusterName
    creation2.clientPort shouldBe clientPort
    creation2.exporterPort shouldBe exporterPort
    creation2.jmxPort shouldBe jmxPort

    val creation3 = BrokerApi.BROKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "name": "$name",
      |    "nodeNames": ["$nodeName"]
      |  }
      """.stripMargin.parseJson)

    creation3.name shouldBe name
    creation3.nodeNames.size shouldBe 1
    creation3.nodeNames.head shouldBe nodeName
    creation3.imageName shouldBe BrokerApi.IMAGE_NAME_DEFAULT
    creation3.clientPort should not be 0
    creation3.exporterPort should not be 0
    creation3.jmxPort should not be 0
  }

  @Test
  def testUpdate(): Unit = {
    val name = CommonUtils.randomString(10)
    val group = CommonUtils.randomString(10)
    val imageName = CommonUtils.randomString()
    val clientPort = CommonUtils.availablePort()
    val nodeName = CommonUtils.randomString()

    val creation = access.name(name).nodeName(nodeName).creation
    creation.name shouldBe name
    // use default values if absent
    creation.group shouldBe GROUP_DEFAULT
    creation.imageName shouldBe BrokerApi.IMAGE_NAME_DEFAULT
    creation.nodeNames shouldBe Set(nodeName)

    // initial a new update request
    val updateAsCreation = BrokerApi.access.request
      .name(name)
      // the group here is not as same as before
      // here we use update as creation
      .group(group)
      .imageName(imageName)
      .clientPort(clientPort)
      .updating
    updateAsCreation.imageName shouldBe Some(imageName)
    updateAsCreation.clientPort shouldBe Some(clientPort)
    updateAsCreation.nodeNames should not be Some(Set(nodeName))
  }

  @Test
  def testDefaultName(): Unit = BrokerApi.BROKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "nodeNames": ["n1"]
      |  }
      """.stripMargin.parseJson).name.nonEmpty shouldBe true

  @Test
  def parseNameField(): Unit = {
    val thrown1 = the[DeserializationException] thrownBy BrokerApi.BROKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "name": ""
      |  }
      |  """.stripMargin.parseJson)
    thrown1.getMessage should include("the value of \"name\" can't be empty string")
  }

  @Test
  def parseImageNameField(): Unit = {
    val thrown2 = the[DeserializationException] thrownBy BrokerApi.BROKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "imageName": ""
      |  }
      |  """.stripMargin.parseJson)
    thrown2.getMessage should include("the value of \"imageName\" can't be empty string")
  }

  @Test
  def parseImageNameOnUpdate(): Unit = {
    val thrown = the[DeserializationException] thrownBy BrokerApi.BROKER_UPDATING_JSON_FORMAT.read(s"""
      |  {
      |    "imageName": ""
      |  }
      """.stripMargin.parseJson)
    thrown.getMessage should include("the value of \"imageName\" can't be empty string")
  }

  @Test
  def testEmptyNodeNames(): Unit =
    an[DeserializationException] should be thrownBy BrokerApi.BROKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "name": "name",
      |    "nodeNames": []
      |  }
      """.stripMargin.parseJson)

  @Test
  def parseNodeNamesOnUpdate(): Unit = {
    val thrown1 = the[DeserializationException] thrownBy BrokerApi.BROKER_UPDATING_JSON_FORMAT.read(s"""
      |  {
      |    "nodeNames": ""
      |  }
      """.stripMargin.parseJson)
    thrown1.getMessage should include("the value of \"nodeNames\" can't be empty string")
  }

  @Test
  def parseZeroClientPort(): Unit =
    an[DeserializationException] should be thrownBy BrokerApi.BROKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "name": "name",
      |    "clientPort": 0,
      |    "nodeNames": ["n"]
      |  }
      """.stripMargin.parseJson)

  @Test
  def parseNegativeClientPort(): Unit =
    an[DeserializationException] should be thrownBy BrokerApi.BROKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "name": "name",
      |    "clientPort": -1,
      |    "nodeNames": ["n"]
      |  }
      """.stripMargin.parseJson)

  @Test
  def parseLargeClientPort(): Unit =
    an[DeserializationException] should be thrownBy BrokerApi.BROKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "name": "name",
      |    "clientPort": 999999,
      |    "nodeNames": ["n"]
      |  }
      """.stripMargin.parseJson)

  @Test
  def parseClientPortOnUpdate(): Unit = {
    val thrown1 = the[DeserializationException] thrownBy BrokerApi.BROKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "clientPort": 0
      |  }
      """.stripMargin.parseJson)
    thrown1.getMessage should include("the connection port must be [1024, 65535)")

    val thrown2 = the[DeserializationException] thrownBy BrokerApi.BROKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "clientPort": -9
      |  }
      """.stripMargin.parseJson)
    thrown2.getMessage should include("the connection port must be [1024, 65535)")

    val thrown3 = the[DeserializationException] thrownBy BrokerApi.BROKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "clientPort": 99999
      |  }
      """.stripMargin.parseJson)
    thrown3.getMessage should include("the connection port must be [1024, 65535), but actual port is \"99999\"")
  }

  @Test
  def parseZeroExporterPort(): Unit =
    an[DeserializationException] should be thrownBy BrokerApi.BROKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "name": "name",
      |    "exporterPort": 0,
      |    "nodeNames": ["n"]
      |  }
      """.stripMargin.parseJson)

  @Test
  def parseNegativeExporterPort(): Unit =
    an[DeserializationException] should be thrownBy BrokerApi.BROKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "name": "name",
      |    "exporterPort": -1,
      |    "nodeNames": ["n"]
      |  }
      """.stripMargin.parseJson)

  @Test
  def parseLargeExporterPort(): Unit =
    an[DeserializationException] should be thrownBy BrokerApi.BROKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "name": "name",
      |    "exporterPort": 999999,
      |    "nodeNames": ["n"]
      |  }
      """.stripMargin.parseJson)

  @Test
  def parseExporterPortOnUpdate(): Unit = {
    val thrown1 = the[DeserializationException] thrownBy BrokerApi.BROKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "exporterPort": 0
      |  }
      """.stripMargin.parseJson)
    thrown1.getMessage should include("the connection port must be [1024, 65535)")

    val thrown2 = the[DeserializationException] thrownBy BrokerApi.BROKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "exporterPort": -9
      |  }
      """.stripMargin.parseJson)
    thrown2.getMessage should include("the connection port must be [1024, 65535)")

    val thrown3 = the[DeserializationException] thrownBy BrokerApi.BROKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "exporterPort": 99999
      |  }
      """.stripMargin.parseJson)
    thrown3.getMessage should include("the connection port must be [1024, 65535), but actual port is \"99999\"")
  }

  @Test
  def parseZeroJmxPort(): Unit =
    an[DeserializationException] should be thrownBy BrokerApi.BROKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "name": "name",
      |    "jmxPort": 0,
      |    "nodeNames": ["n"]
      |  }
      """.stripMargin.parseJson)

  @Test
  def parseNegativeJmxPort(): Unit =
    an[DeserializationException] should be thrownBy BrokerApi.BROKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "name": "name",
      |    "jmxPort": -1,
      |    "nodeNames": ["n"]
      |  }
      """.stripMargin.parseJson)

  @Test
  def parseLargeJmxPort(): Unit =
    an[DeserializationException] should be thrownBy BrokerApi.BROKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "name": "name",
      |    "jmxPort": 999999,
      |    "nodeNames": ["n"]
      |  }
      """.stripMargin.parseJson)

  @Test
  def parseJmxPortOnUpdate(): Unit = {
    val thrown1 = the[DeserializationException] thrownBy BrokerApi.BROKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "jmxPort": 0
      |  }
      """.stripMargin.parseJson)
    thrown1.getMessage should include("the connection port must be [1024, 65535)")

    val thrown2 = the[DeserializationException] thrownBy BrokerApi.BROKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "jmxPort": -9
      |  }
      """.stripMargin.parseJson)
    thrown2.getMessage should include("the connection port must be [1024, 65535)")

    val thrown3 = the[DeserializationException] thrownBy BrokerApi.BROKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "jmxPort": 99999
      |  }
      """.stripMargin.parseJson)
    thrown3.getMessage should include("the connection port must be [1024, 65535), but actual port is \"99999\"")
  }

  @Test
  def testInvalidNodeNames(): Unit = {
    an[DeserializationException] should be thrownBy access.nodeName("start").creation
    an[DeserializationException] should be thrownBy access.nodeName("stop").creation
    an[DeserializationException] should be thrownBy access.nodeName("start").updating
    an[DeserializationException] should be thrownBy access.nodeName("stop").updating

    an[DeserializationException] should be thrownBy BrokerApi.BROKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "nodeNames": ["start", "stop"]
      |  }
      """.stripMargin.parseJson)
  }

  @Test
  def testInvalidBrokerClusterKey(): Unit = {
    an[DeserializationException] should be thrownBy BrokerApi.BROKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "zookeeperClusterKey": "",
      |    "nodeNames": ["n1"]
      |  }
      """.stripMargin.parseJson)
  }

  @Test
  def testBrokerClusterKeyWithDefaultGroup(): Unit = {
    val name = CommonUtils.randomString()
    BrokerApi.BROKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "zookeeperClusterKey": "$name",
      |    "nodeNames": ["n1"]
      |  }
      """.stripMargin.parseJson).zookeeperClusterKey.get shouldBe ObjectKey.of(GROUP_DEFAULT, name)
  }

  @Test
  def testStaleBrokerClusterName(): Unit = {
    val name = CommonUtils.randomString()
    BrokerApi.BROKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "zookeeperClusterName": "$name",
      |    "nodeNames": ["n1"]
      |  }
      """.stripMargin.parseJson).zookeeperClusterKey.get shouldBe ObjectKey.of(GROUP_DEFAULT, name)
  }

  @Test
  def testDefaultUpdate(): Unit = {
    val data = access.name(CommonUtils.randomString(10)).updating
    data.imageName.isEmpty shouldBe true
    data.zookeeperClusterKey.isEmpty shouldBe true
    data.exporterPort.isEmpty shouldBe true
    data.jmxPort.isEmpty shouldBe true
    data.clientPort.isEmpty shouldBe true
    data.nodeNames.isEmpty shouldBe true
  }

  @Test
  def testEmptyString(): Unit = {
    an[DeserializationException] should be thrownBy BrokerApi.BROKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "name": "",
      |    "nodeNames": ["a0"]
      |  }
      """.stripMargin.parseJson)

    an[DeserializationException] should be thrownBy BrokerApi.BROKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "name": "name",
      |    "zookeeperClusterName": "",
      |    "nodeNames": ["a0"]
      |  }
      """.stripMargin.parseJson)

    an[DeserializationException] should be thrownBy BrokerApi.BROKER_CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "name": "name",
      |    "imageName": "",
      |    "nodeNames": ["a0"]
      |  }
      """.stripMargin.parseJson)
  }

  @Test
  def groupShouldAppearInResponse(): Unit = {
    val name = CommonUtils.randomString(5)
    val res = BrokerApi.BROKER_CLUSTER_INFO_JSON_FORMAT.write(
      BrokerClusterInfo(
        settings = BrokerApi.access.request.name(name).nodeNames(Set("n1")).creation.settings,
        aliveNodes = Set.empty,
        state = None,
        error = None,
        lastModified = CommonUtils.current(),
        topicSettingDefinitions = Seq.empty
      ))
    // serialize to json should see the object key (group, name)
    res.asJsObject.fields("settings").asJsObject.fields(NAME_KEY).convertTo[String] shouldBe name
    res.asJsObject.fields("settings").asJsObject.fields(GROUP_KEY).convertTo[String] shouldBe GROUP_DEFAULT

    // // deserialize to info should see the object key (group, name)
    val data = BrokerApi.BROKER_CLUSTER_INFO_JSON_FORMAT.read(res)
    data.name shouldBe name
    data.group shouldBe GROUP_DEFAULT
  }

  @Test
  def testTagsOnUpdate(): Unit = access.updating.tags shouldBe None

  @Test
  def testOverwriteSettings(): Unit = {
    val r1 =
      access.nodeName("n1").clientPort(12345).jmxPort(45678).creation

    val r2 = access.nodeName("n1").clientPort(12345).settings(Map("name" -> JsString("fake"))).creation

    r1.nodeNames shouldBe r2.nodeNames
    r1.clientPort shouldBe r2.clientPort
    // settings will overwrite default value
    r1.name should not be r2.name
  }

  @Test
  def testDeadNodes(): Unit = {
    val cluster = BrokerClusterInfo(
      settings = BrokerApi.access.request.nodeNames(Set("n0", "n1")).creation.settings,
      aliveNodes = Set("n0"),
      state = Some("running"),
      error = None,
      lastModified = CommonUtils.current(),
      topicSettingDefinitions = Seq.empty
    )
    cluster.deadNodes shouldBe Set("n1")
    cluster.copy(state = None).deadNodes shouldBe Set.empty
  }

  @Test
  def testConnectionProps(): Unit = {
    val cluster = BrokerClusterInfo(
      settings = BrokerApi.access.request.nodeNames(Set("n0", "m1")).creation.settings,
      aliveNodes = Set("nn"),
      state = Some("running"),
      error = None,
      lastModified = CommonUtils.current(),
      topicSettingDefinitions = Seq.empty
    )
    cluster.connectionProps should not include "nn"
  }
}
