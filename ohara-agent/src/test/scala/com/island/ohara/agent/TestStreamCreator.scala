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

package com.island.ohara.agent

import java.net.URL
import java.util.Objects

import com.island.ohara.client.configurator.v0.FileInfoApi.FileInfo
import com.island.ohara.client.configurator.v0.MetricsApi.Metrics
import com.island.ohara.client.configurator.v0.StreamApi.StreamClusterInfo
import com.island.ohara.client.configurator.v0.{FileInfoApi, StreamApi, TopicApi}
import com.island.ohara.common.rule.SmallTest
import com.island.ohara.common.setting.TopicKey
import com.island.ohara.common.util.CommonUtils
import com.island.ohara.streams.config.StreamDefUtils
import org.junit.Test
import org.scalatest.Matchers
import spray.json._

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class TestStreamCreator extends SmallTest with Matchers {

  private[this] def topicKey(): TopicKey = topicKey(CommonUtils.randomString())
  private[this] def topicKey(name: String): TopicKey = TopicKey.of(TopicApi.GROUP_DEFAULT, name)

  private[this] def streamCreator(): StreamCollie.ClusterCreator =
    (clusterName,
     nodeNames,
     imageName,
     brokerClusterName,
     jarInfo,
     jmxPort,
     fromTopics,
     toTopics,
     settings,
     executionContext) => {
      // We only check required variables
      CommonUtils.requireNonEmpty(clusterName)
      CommonUtils.requireNonEmpty(nodeNames.asJava)
      CommonUtils.requireNonEmpty(imageName)
      Objects.requireNonNull(jarInfo)
      CommonUtils.requireConnectionPort(jmxPort)
      CommonUtils.requireNonEmpty(brokerClusterName)
      CommonUtils.requireNonEmpty(fromTopics.asJava)
      CommonUtils.requireNonEmpty(toTopics.asJava)
      Objects.requireNonNull(settings)
      Objects.requireNonNull(executionContext)
      Future.successful {
        StreamClusterInfo(
          settings = settings,
          nodeNames = nodeNames,
          deadNodes = Set.empty,
          metrics = Metrics.EMPTY,
          state = None,
          error = None,
          lastModified = CommonUtils.current()
        )
      }
    }

  private[this] def result[T](f: Future[T]): T = Await.result(f, 10 seconds)

  @Test
  def nullClusterName(): Unit = {
    an[NullPointerException] should be thrownBy streamCreator().clusterName(null)
  }

  @Test
  def IllegalClusterName(): Unit = {
    an[DeserializationException] should be thrownBy streamCreator().clusterName("!@#$-")
  }

  @Test
  def nullNodeName(): Unit = {
    an[NullPointerException] should be thrownBy streamCreator().nodeNames(null)
  }

  @Test
  def emptyNodeName(): Unit = {
    an[IllegalArgumentException] should be thrownBy streamCreator().nodeNames(Set.empty)
  }

  @Test
  def nullImage(): Unit = {
    an[NullPointerException] should be thrownBy streamCreator().imageName(null)
  }

  @Test
  def emptyImage(): Unit = {
    an[IllegalArgumentException] should be thrownBy streamCreator().imageName("")
  }

  @Test
  def nullJarInfo(): Unit = {
    an[NullPointerException] should be thrownBy streamCreator().jarInfo(null)
  }

  @Test
  def nullSettings(): Unit = an[NullPointerException] should be thrownBy streamCreator().settings(null)

  private[this] def fileInfo: FileInfo = fileInfo(new URL("http://abc/aaa.jar"))

  private[this] def fileInfo(url: URL): FileInfo = FileInfo(
    group = CommonUtils.randomString(),
    name = CommonUtils.randomString(),
    size = 1000,
    url = url,
    lastModified = CommonUtils.current(),
    tags = Map.empty
  )

  @Test
  def testNameLength(): Unit =
    streamCreator()
      .clusterName(CommonUtils.randomString(StreamApi.LIMIT_OF_NAME_LENGTH))
      .imageName(CommonUtils.randomString())
      .brokerClusterName(CommonUtils.randomString())
      .settings(Map(
        StreamDefUtils.TO_TOPIC_KEYS_DEFINITION.key() -> JsArray(TopicKey.toJsonString(topicKey()).parseJson),
        StreamDefUtils.FROM_TOPIC_KEYS_DEFINITION.key() -> JsArray(TopicKey.toJsonString(topicKey()).parseJson),
        StreamDefUtils.JMX_PORT_DEFINITION.key() -> JsNumber(1000)
      ))
      .nodeName(CommonUtils.randomString())
      .jarInfo(fileInfo)
      .create()

  an[DeserializationException] should be thrownBy streamCreator()
    .clusterName(CommonUtils.randomString(StreamApi.LIMIT_OF_NAME_LENGTH + 1))
    .imageName(CommonUtils.randomString())
    .brokerClusterName(CommonUtils.randomString())
    .jarInfo(fileInfo)
    .settings(Map(
      StreamDefUtils.TO_TOPIC_KEYS_DEFINITION.key() -> JsArray(TopicKey.toJsonString(topicKey()).parseJson),
      StreamDefUtils.FROM_TOPIC_KEYS_DEFINITION.key() -> JsArray(TopicKey.toJsonString(topicKey()).parseJson),
      StreamDefUtils.JMX_PORT_DEFINITION.key() -> JsNumber(1000)
    ))
    .nodeName(CommonUtils.randomString())
    .create()

  @Test
  def testCopy(): Unit = {
    val info = StreamClusterInfo(
      settings = Map(
        StreamDefUtils.NAME_DEFINITION.key() -> JsString("name"),
        StreamDefUtils.IMAGE_NAME_DEFINITION.key() -> JsString("imageName"),
        StreamDefUtils.BROKER_CLUSTER_NAME_DEFINITION.key() -> JsString("BK"),
        StreamDefUtils.INSTANCES_DEFINITION.key() -> JsNumber(1),
        StreamDefUtils.TO_TOPIC_KEYS_DEFINITION.key() -> JsArray(TopicKey.toJsonString(topicKey()).parseJson),
        StreamDefUtils.FROM_TOPIC_KEYS_DEFINITION.key() -> JsArray(TopicKey.toJsonString(topicKey()).parseJson),
        StreamDefUtils.JMX_PORT_DEFINITION.key() -> JsNumber(100),
        StreamDefUtils.TAGS_DEFINITION.key() -> JsObject(Map("bar" -> JsString("foo"), "he" -> JsNumber(1))),
        StreamDefUtils.JAR_INFO_DEFINITION.key() -> FileInfoApi.FILE_INFO_JSON_FORMAT.write(fileInfo)
      ),
      nodeNames = Set("node1"),
      deadNodes = Set.empty,
      state = None,
      error = None,
      metrics = Metrics.EMPTY,
      lastModified = CommonUtils.current()
    )

    // pass
    result(streamCreator().copy(info).create())
  }

  @Test
  def testNormalCase(): Unit =
    // could set jmx port
    result(
      streamCreator()
        .clusterName(CommonUtils.randomString(StreamApi.LIMIT_OF_NAME_LENGTH))
        .imageName(CommonUtils.randomString())
        .brokerClusterName(CommonUtils.randomString())
        .jarInfo(fileInfo)
        .settings(Map(
          StreamDefUtils.TO_TOPIC_KEYS_DEFINITION.key() -> JsArray(TopicKey.toJsonString(topicKey()).parseJson),
          StreamDefUtils.FROM_TOPIC_KEYS_DEFINITION.key() -> JsArray(TopicKey.toJsonString(topicKey()).parseJson),
          StreamDefUtils.JMX_PORT_DEFINITION.key() -> JsNumber(1000)
        ))
        .nodeName(CommonUtils.randomString())
        .create()).jmxPort shouldBe 1000

  @Test
  def testParseJarKey(): Unit = {
    //a normal url
    val jarInfo = fileInfo(new URL("http://localhost:12345/group/abc.jar"))
    val res = result(
      streamCreator()
        .clusterName(CommonUtils.randomString(StreamApi.LIMIT_OF_NAME_LENGTH))
        .imageName(CommonUtils.randomString())
        .brokerClusterName(CommonUtils.randomString())
        .nodeName(CommonUtils.randomString())
        .settings(Map(
          StreamDefUtils.TO_TOPIC_KEYS_DEFINITION.key() -> JsArray(TopicKey.toJsonString(topicKey()).parseJson),
          StreamDefUtils.FROM_TOPIC_KEYS_DEFINITION.key() -> JsArray(TopicKey.toJsonString(topicKey()).parseJson),
          StreamDefUtils.JMX_PORT_DEFINITION.key() -> JsNumber(1000)
        ))
        .jarInfo(jarInfo)
        .create())
    res.jarKey.group() shouldBe jarInfo.group
    res.jarKey.name() shouldBe jarInfo.name
  }

  @Test
  def ignoreFromTopic(): Unit =
    intercept[NoSuchElementException] {
      result(
        streamCreator()
          .clusterName(CommonUtils.randomString(StreamApi.LIMIT_OF_NAME_LENGTH))
          .imageName(CommonUtils.randomString())
          .brokerClusterName(CommonUtils.randomString())
          .jarInfo(fileInfo)
          .settings(Map(
            StreamDefUtils.TO_TOPIC_KEYS_DEFINITION.key() -> JsArray(TopicKey.toJsonString(topicKey()).parseJson),
            StreamDefUtils.JMX_PORT_DEFINITION.key() -> JsNumber(1000)
          ))
          .nodeName(CommonUtils.randomString())
          .create())
    }.getMessage should include(StreamDefUtils.FROM_TOPIC_KEYS_DEFINITION.key())

  @Test
  def ignoreToTopic(): Unit =
    intercept[NoSuchElementException] {
      streamCreator()
        .clusterName(CommonUtils.randomString(StreamApi.LIMIT_OF_NAME_LENGTH))
        .imageName(CommonUtils.randomString())
        .brokerClusterName(CommonUtils.randomString())
        .jarInfo(fileInfo)
        .settings(Map(
          StreamDefUtils.FROM_TOPIC_KEYS_DEFINITION.key() -> JsArray(TopicKey.toJsonString(topicKey()).parseJson),
          StreamDefUtils.JMX_PORT_DEFINITION.key() -> JsNumber(1000)
        ))
        .nodeName(CommonUtils.randomString())
        .create()
    }.getMessage should include(StreamDefUtils.TO_TOPIC_KEYS_DEFINITION.key())

  @Test
  def ignoreJmxPort(): Unit =
    intercept[NoSuchElementException] {
      streamCreator()
        .clusterName(CommonUtils.randomString(StreamApi.LIMIT_OF_NAME_LENGTH))
        .imageName(CommonUtils.randomString())
        .brokerClusterName(CommonUtils.randomString())
        .jarInfo(fileInfo)
        .settings(Map(
          StreamDefUtils.TO_TOPIC_KEYS_DEFINITION.key() -> JsArray(TopicKey.toJsonString(topicKey()).parseJson),
          StreamDefUtils.FROM_TOPIC_KEYS_DEFINITION.key() -> JsArray(TopicKey.toJsonString(topicKey()).parseJson)
        ))
        .nodeName(CommonUtils.randomString())
        .create()
    }.getMessage should include(StreamDefUtils.JMX_PORT_DEFINITION.key())
}
