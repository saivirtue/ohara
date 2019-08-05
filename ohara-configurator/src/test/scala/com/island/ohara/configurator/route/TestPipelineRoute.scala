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

import com.island.ohara.client.configurator.v0.PipelineApi.Flow
import com.island.ohara.client.configurator.v0._
import com.island.ohara.common.rule.MediumTest
import com.island.ohara.common.setting.ObjectKey
import com.island.ohara.common.util.{CommonUtils, Releasable}
import com.island.ohara.configurator.{Configurator, DumbSink}
import org.junit.{After, Test}
import org.scalatest.Matchers
import spray.json.{JsNumber, JsString}

import scala.concurrent.ExecutionContext.Implicits.global

// there are too many test cases in this file so we promote  it from small test to medium test
class TestPipelineRoute extends MediumTest with Matchers {
  private[this] val configurator = Configurator.builder.fake(1, 1).build()

  private[this] val workerApi = WorkerApi.access.hostname(configurator.hostname).port(configurator.port)

  private[this] val pipelineApi = PipelineApi.access.hostname(configurator.hostname).port(configurator.port)

  private[this] val connectorApi = ConnectorApi.access.hostname(configurator.hostname).port(configurator.port)

  private[this] val topicApi = TopicApi.access.hostname(configurator.hostname).port(configurator.port)

  private[this] val streamApi = StreamApi.access.hostname(configurator.hostname).port(configurator.port)

  @Test
  def testFlowAndObjects(): Unit = {
    val topic = result(topicApi.request.name(CommonUtils.randomString(10)).create())
    result(topicApi.start(topic.key))

    val connector = result(
      connectorApi.request
        .name(CommonUtils.randomString(10))
        .className(classOf[DumbSink].getName)
        .numberOfTasks(1)
        .topicKey(topic.key)
        .create())
    result(connectorApi.start(connector.key))

    var pipeline = result(
      pipelineApi.request.name(CommonUtils.randomString(10)).flow(Flow(connector.key, Set(topic.key))).create()
    )

    pipeline.flows.size shouldBe 1
    pipeline.flows.head.from shouldBe connector.key
    pipeline.flows.head.to shouldBe Set(topic.key)
    pipeline.objects.size shouldBe 2

    result(topicApi.stop(topic.key))
    // remove topic
    result(topicApi.delete(topic.key))

    pipeline = result(pipelineApi.get(pipeline.key))

    // topic is gone
    pipeline.objects.size shouldBe 1

    val pipelines = result(pipelineApi.list())

    pipelines.size shouldBe 1
    pipelines.head.flows.size shouldBe 1
    // topic is gone
    pipelines.head.objects.size shouldBe 1

    // remove worker cluster
    val wk = result(workerApi.list()).head.name
    result(workerApi.stop(wk))
    result(workerApi.delete(wk))

    // worker cluster is gone so the object abstract should contain error
    pipeline = result(pipelineApi.get(pipeline.key))
    // topic is gone
    pipeline.objects.size shouldBe 1
    pipeline.objects.head.error should not be None

  }

  @Test
  def testAddStreamAppToPipeline(): Unit = {
    // create worker
    val nodes = result(configurator.nodeCollie.nodes()).map(_.name).toSet
    val wk = result(workerApi.request.nodeNames(nodes).create())

    // create an empty streamApp
    val streamApp = result(streamApi.request.create())

    val pipeline = result(
      pipelineApi.request.name(CommonUtils.randomString(10)).flow(Flow(streamApp.key, Set.empty)).create()
    )
    pipeline.flows.size shouldBe 1
    pipeline.flows.head.from shouldBe streamApp.key
    pipeline.flows.head.to shouldBe Set.empty
    pipeline.objects.size shouldBe 1
    // we cannot parse class name from empty jar
    pipeline.objects.head.className shouldBe None

    // remove worker cluster
    result(workerApi.delete(wk.name))

    // worker cluster is gone so the object abstract should contain error
    result(pipelineApi.get(pipeline.key)).objects.head.error should not be None
  }

  @Test
  def testNonexistentData(): Unit = {
    val topic = result(topicApi.request.name(CommonUtils.randomString(10)).create())
    val name = CommonUtils.randomString()
    val flow = Flow(from = topic.key, to = Set(ObjectKey.of(CommonUtils.randomString(), CommonUtils.randomString())))
    val pipeline = result(pipelineApi.request.name(name).flow(flow).create())
    // the "to" is reference to an nonexistent data
    pipeline.objects.size shouldBe 1
  }

  @Test
  def addMultiPipelines(): Unit = {
    val topic0 = result(topicApi.request.name(CommonUtils.randomString(10)).create())

    val topic1 = result(topicApi.request.name(CommonUtils.randomString(10)).create())

    val pipeline0 = result(pipelineApi.request.name(CommonUtils.randomString()).flow(topic0.key, topic1.key).create())

    val pipeline1 = result(pipelineApi.request.name(CommonUtils.randomString()).flow(topic0.key, topic1.key).create())

    val pipelines = result(pipelineApi.list())
    pipelines.size shouldBe 2
    pipelines.exists(_.name == pipeline0.name) shouldBe true
    pipelines.exists(_.name == pipeline1.name) shouldBe true
  }

  @Test
  def listConnectorWhichIsNotRunning(): Unit = {
    val topic = result(topicApi.request.name(CommonUtils.randomString(10)).create())
    result(topicApi.start(topic.key))

    val connector = result(
      connectorApi.request
        .name(CommonUtils.randomString(10))
        .className(classOf[DumbSink].getName)
        .numberOfTasks(1)
        .create())

    val pipeline = result(pipelineApi.request.name(CommonUtils.randomString()).flow(topic.key, connector.key).create())

    pipeline.objects.size shouldBe 2
    pipeline.objects.foreach { obj =>
      obj.error shouldBe None
      obj.state shouldBe None
    }
  }

  @Test
  def testRunningConnector(): Unit = {
    val topic = result(topicApi.request.name(CommonUtils.randomString(10)).create())
    result(topicApi.start(topic.key))
    val connector = result(
      connectorApi.request
        .name(CommonUtils.randomString(10))
        .className(classOf[DumbSink].getName)
        .numberOfTasks(1)
        .topicKey(topic.key)
        .create())

    result(connectorApi.start(connector.key))

    val pipeline = result(
      pipelineApi.request.name(CommonUtils.randomString()).flow(connector.key, connector.key).create())

    // duplicate object is removed
    pipeline.objects.size shouldBe 1
    pipeline.objects.foreach { obj =>
      obj.error shouldBe None
      obj.state should not be None
    }
  }

  @Test
  def nonexistentConnectorClass(): Unit = {
    val topic = result(topicApi.request.name(CommonUtils.randomString(10)).create())
    result(topicApi.start(topic.key))

    val connector = result(
      connectorApi.request
        .name(CommonUtils.randomString(10))
        .className(CommonUtils.randomString(10))
        .numberOfTasks(1)
        .topicKey(topic.key)
        .create())
    result(connectorApi.start(connector.key))

    val pipeline = result(pipelineApi.request.name(CommonUtils.randomString()).flow(topic.key, connector.key).create())

    pipeline.objects.size shouldBe 2
    pipeline.objects.filter(_.name == connector.name).foreach { obj =>
      obj.error.isEmpty shouldBe false
      obj.state shouldBe None
    }
  }

  @Test
  def duplicateDelete(): Unit =
    (0 to 10).foreach(_ =>
      result(pipelineApi.delete(ObjectKey.of(CommonUtils.randomString(5), CommonUtils.randomString(5)))))

  @Test
  def duplicateUpdate(): Unit = {
    val count = 10
    (0 until count).foreach(_ => result(pipelineApi.request.name(CommonUtils.randomString()).flows(Seq.empty).update()))
    result(pipelineApi.list()).size shouldBe count
  }

  @Test
  def updatingNonexistentNameCanNotIgnoreFlows(): Unit = {
    val name = CommonUtils.randomString()
    val flows: Seq[Flow] = Seq.empty
    val pipeline = result(pipelineApi.request.name(name).flows(flows).update())
    result(pipelineApi.list()).size shouldBe 1
    pipeline.name shouldBe name
    pipeline.flows shouldBe flows
  }

  @Test
  def updateOnlyFlow(): Unit = {
    val topic = result(topicApi.request.name(CommonUtils.randomString(10)).create())
    val pipeline = result(
      pipelineApi.request.name(CommonUtils.randomString()).flow(topic.key, Set.empty[ObjectKey]).update())
    pipeline.flows.size shouldBe 1
    pipeline.flows.head.from shouldBe topic.key
    pipeline.flows.head.to.size shouldBe 0

    val pipeline2 = result(pipelineApi.request.key(pipeline.key).flows(Seq.empty).update())
    result(pipelineApi.list()).size shouldBe 1
    pipeline2.name shouldBe pipeline.name
    pipeline2.flows shouldBe Seq.empty
  }

  @Test
  def testDuplicateObjectName(): Unit = {
    val name = CommonUtils.randomString(10)
    val topic = result(topicApi.request.name(name).create())

    val connector = result(
      connectorApi.request
        .name(name)
        .className(classOf[DumbSink].getName)
        .topicKey(topic.key)
        .numberOfTasks(1)
        .create())

    val pipeline = result(pipelineApi.request.name(methodName()).flow(topic.key, connector.key).create())
    pipeline.flows.size shouldBe 1
    pipeline.objects.size shouldBe 2
  }

  @Test
  def updateTags(): Unit = {
    val tags = Map(
      CommonUtils.randomString(10) -> JsString(CommonUtils.randomString(10)),
      CommonUtils.randomString(10) -> JsNumber(CommonUtils.randomInteger())
    )
    val pipelineDesc = result(pipelineApi.request.tags(tags).create())
    pipelineDesc.tags shouldBe tags

    val tags2 = Map(
      CommonUtils.randomString(10) -> JsString(CommonUtils.randomString(10)),
      CommonUtils.randomString(10) -> JsNumber(CommonUtils.randomInteger())
    )
    val pipelineDesc2 = result(pipelineApi.request.name(pipelineDesc.name).tags(tags2).update())
    pipelineDesc2.tags shouldBe tags2

    val pipelineDesc3 = result(pipelineApi.request.name(pipelineDesc.name).update())
    pipelineDesc3.tags shouldBe tags2

    val pipelineDesc4 = result(pipelineApi.request.name(pipelineDesc.name).tags(Map.empty).update())
    pipelineDesc4.tags shouldBe Map.empty
  }

  @Test
  def testGroup(): Unit = {
    // default group
    result(pipelineApi.request.create()).group shouldBe PipelineApi.GROUP_DEFAULT

    val group = CommonUtils.randomString()
    val ftpInfo = result(pipelineApi.request.group(group).create())
    ftpInfo.group shouldBe group

    result(pipelineApi.list()).size shouldBe 2

    // update an existent object
    result(pipelineApi.request.group(ftpInfo.group).name(ftpInfo.name).update())

    result(pipelineApi.list()).size shouldBe 2

    // update an nonexistent (different group) object
    val group2 = CommonUtils.randomString()
    result(pipelineApi.request.group(group2).name(ftpInfo.name).create()).group shouldBe group2

    result(pipelineApi.list()).size shouldBe 3
  }

  @Test
  def testDuplicateObjects(): Unit = {
    val topic = result(topicApi.request.name(CommonUtils.randomString(10)).create())

    val pipeline = result(
      pipelineApi.request.flows(Seq(Flow(topic.key, Set(topic.key)), Flow(topic.key, Set(topic.key)))).create())

    pipeline.objects.size shouldBe 1
    pipeline.objects.head.key shouldBe topic.key
  }

  @Test
  def connectorObjectAlwaysCarryClassName(): Unit = {
    val topic = result(topicApi.request.name(CommonUtils.randomString(10)).create())
    val className = CommonUtils.randomString()
    val connector = result(
      connectorApi.request
        .name(CommonUtils.randomString(10))
        .className(className)
        // unknown worker
        .workerClusterName(CommonUtils.randomString())
        .create())
    val pipeline = result(pipelineApi.request.flow(Flow(connector.key, Set(topic.key))).create())
    pipeline.objects.size shouldBe 2
    pipeline.objects.find(_.key == connector.key).get.className shouldBe Some(className)
  }

  @Test
  def testRefresh(): Unit = {
    val topic = result(topicApi.request.name(CommonUtils.randomString(10)).create())
    val connector = result(connectorApi.request.name(CommonUtils.randomString(10)).create())
    val pipeline = result(
      pipelineApi.request
      // the first flow contains the topic in "to"
      // the second flow contains the topic in "from"
        .flows(Seq(Flow(connector.key, Set(topic.key)), Flow(topic.key, Set(connector.key))))
        .create())
    pipeline.objects.size shouldBe 2

    result(topicApi.delete(topic.key))
    result(pipelineApi.refresh(pipeline.key))

    // the topic is removed so
    // 1) the first flow should have nothing in "to"
    // 2) the second flow should be removed since it have nothing in "from"
    result(pipelineApi.get(pipeline.key)).flows.size shouldBe 1
    result(pipelineApi.get(pipeline.key)).flows.head.to shouldBe Set.empty
    result(pipelineApi.get(pipeline.key)).objects.size shouldBe 1
  }

  @After
  def tearDown(): Unit = Releasable.close(configurator)
}
