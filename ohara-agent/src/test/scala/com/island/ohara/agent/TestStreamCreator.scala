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

import com.island.ohara.client.configurator.v0.MetricsApi.Metrics
import com.island.ohara.client.configurator.v0.StreamApi.StreamClusterInfo
import com.island.ohara.client.configurator.v0.{Definition, StreamApi}
import com.island.ohara.common.rule.SmallTest
import com.island.ohara.common.setting.SettingDef
import com.island.ohara.common.util.CommonUtils
import com.island.ohara.streams.config.StreamDefinitions.DefaultConfigs
import org.junit.Test
import org.scalatest.Matchers
import spray.json.{DeserializationException, JsArray, JsNumber, JsObject, JsString}

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class TestStreamCreator extends SmallTest with Matchers {

  private[this] def streamCreator(): StreamCollie.ClusterCreator =
    (clusterName, nodeNames, imageName, jarUrl, jmxPort, settings, executionContext) => {
      // We only check required variables
      CommonUtils.requireNonEmpty(clusterName)
      CommonUtils.requireNonEmpty(nodeNames.asJava)
      CommonUtils.requireNonEmpty(imageName)
      Objects.requireNonNull(jarUrl)
      CommonUtils.requireConnectionPort(jmxPort)
      Objects.requireNonNull(settings)
      Objects.requireNonNull(executionContext)
      Future.successful {
        val jarKey = StreamCollie.urlToDataKey(jarUrl.toString)
        StreamClusterInfo(
          settings = Map(
            DefaultConfigs.NAME_DEFINITION.key() -> JsString(clusterName),
            DefaultConfigs.IMAGE_NAME_DEFINITION.key() -> JsString(imageName),
            DefaultConfigs.INSTANCES_DEFINITION.key() -> JsNumber(nodeNames.size),
            DefaultConfigs.JAR_KEY_DEFINITION.key() -> JsObject(
              com.island.ohara.client.configurator.v0.GROUP_KEY -> JsString(jarKey.group()),
              com.island.ohara.client.configurator.v0.NAME_KEY -> JsString(jarKey.name())),
            DefaultConfigs.FROM_TOPICS_DEFINITION.key() -> JsArray(
              JsString(settings(DefaultConfigs.FROM_TOPICS_DEFINITION.key()))
            ),
            DefaultConfigs.TO_TOPICS_DEFINITION.key() -> JsArray(
              JsString(settings(DefaultConfigs.TO_TOPICS_DEFINITION.key()))
            ),
            DefaultConfigs.JMX_PORT_DEFINITION.key() -> JsNumber(jmxPort),
          ),
          definition = None,
          nodeNames = nodeNames,
          deadNodes = Set.empty,
          metrics = Metrics(Seq.empty),
          state = None,
          error = None,
          lastModified = CommonUtils.current()
        )
      }
    }

  private[this] def awaitResult[T](f: Future[T]): T = Await.result(f, 10 seconds)

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
  def nullJarUrl(): Unit = {
    an[NullPointerException] should be thrownBy streamCreator().jarUrl(null)
  }

  @Test
  def zeroJmxPort(): Unit = {
    an[IllegalArgumentException] should be thrownBy streamCreator().jmxPort(0)
  }

  @Test
  def negativeJmxPort(): Unit = {
    an[IllegalArgumentException] should be thrownBy streamCreator().jmxPort(-99)
  }

  @Test
  def nullSettings(): Unit = an[NullPointerException] should be thrownBy streamCreator().settings(null)

  @Test
  def testNameLength(): Unit = {
    streamCreator()
      .clusterName(CommonUtils.randomString(StreamApi.LIMIT_OF_NAME_LENGTH))
      .imageName(CommonUtils.randomString())
      .jarUrl(new URL("http://abc/jar"))
      .settings(
        Map(
          DefaultConfigs.FROM_TOPICS_DEFINITION.key() -> "from",
          DefaultConfigs.TO_TOPICS_DEFINITION.key() -> "to"
        ))
      .nodeNames(Set("bar", "foo", "bez"))
      .create()

    an[DeserializationException] should be thrownBy streamCreator()
      .clusterName(CommonUtils.randomString(StreamApi.LIMIT_OF_NAME_LENGTH + 1))
      .imageName(CommonUtils.randomString())
      .jarUrl(new URL("http://abc/jar"))
      .nodeNames(Set("bar", "foo", "bez"))
      .create()
  }

  @Test
  def testCopy(): Unit = {
    val info = StreamClusterInfo(
      settings = Map(
        DefaultConfigs.NAME_DEFINITION.key() -> JsString("name"),
        DefaultConfigs.IMAGE_NAME_DEFINITION.key() -> JsString("imageName"),
        DefaultConfigs.INSTANCES_DEFINITION.key() -> JsNumber(1),
        DefaultConfigs.FROM_TOPICS_DEFINITION.key() -> JsString("aa"),
        DefaultConfigs.TO_TOPICS_DEFINITION.key() -> JsString("bb"),
        DefaultConfigs.JMX_PORT_DEFINITION.key() -> JsNumber(0),
        DefaultConfigs.TAGS_DEFINITION.key() -> JsObject(Map("bar" -> JsString("foo"), "he" -> JsNumber(1)))
      ),
      definition = Some(Definition("className", Seq(SettingDef.builder().key("key").group("group").build()))),
      nodeNames = Set("node1"),
      deadNodes = Set.empty,
      state = None,
      error = None,
      metrics = Metrics(Seq.empty),
      lastModified = CommonUtils.current()
    )

    // copy in stream is not support yet
    an[NullPointerException] should be thrownBy awaitResult(streamCreator().copy(info).create())
  }

  @Test
  def testNormalCase(): Unit = {

    // could set jmx port
    awaitResult(
      streamCreator()
        .clusterName(CommonUtils.randomString(StreamApi.LIMIT_OF_NAME_LENGTH))
        .imageName(CommonUtils.randomString())
        .jarUrl(new URL("http://abc/jar"))
        .settings(Map(
          DefaultConfigs.FROM_TOPICS_DEFINITION.key() -> "from",
          DefaultConfigs.TO_TOPICS_DEFINITION.key() -> "to"
        ))
        .nodeNames(Set("bar", "foo"))
        .jmxPort(1000)
        .create()).jmxPort shouldBe 1000
  }

  @Test
  def testParseJarKey(): Unit = {
    //a normal url
    val url = new URL("http://localhost:12345/group/abc.jar")
    val res = awaitResult(
      streamCreator()
        .clusterName(CommonUtils.randomString(StreamApi.LIMIT_OF_NAME_LENGTH))
        .imageName(CommonUtils.randomString())
        .nodeNames(Set("bar", "foo"))
        .settings(
          Map(
            DefaultConfigs.FROM_TOPICS_DEFINITION.key() -> "from",
            DefaultConfigs.TO_TOPICS_DEFINITION.key() -> "to"
          ))
        .jarUrl(url)
        .create())
    res.jarKey.group() shouldBe "group"
    res.jarKey.name() shouldBe "abc.jar"
  }

  @Test
  def testParseUrl(): Unit = StreamCollie.urlEncode(new URL("http://abc/def/my bar/foo fake.jar"))
}
