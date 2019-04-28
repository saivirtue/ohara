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

package com.island.ohara.configurator

import com.island.ohara.client.configurator.v0.ConnectorApi.ConnectorCreationRequest
import com.island.ohara.client.configurator.v0.PipelineApi.Flow
import com.island.ohara.client.configurator.v0.TopicApi.TopicCreationRequest
import com.island.ohara.client.configurator.v0.{ConnectorApi, PipelineApi, TopicApi}
import com.island.ohara.common.util.{CommonUtils, Releasable}
import com.island.ohara.metrics.BeanChannel
import com.island.ohara.testing.WithBrokerWorker
import org.junit.{After, Test}
import org.scalatest.Matchers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.collection.JavaConverters._
class TestMetrics extends WithBrokerWorker with Matchers {

  private[this] val configurator =
    Configurator.builder().fake(testUtil.brokersConnProps, testUtil().workersConnProps()).build()

  private[this] val connectorApi = ConnectorApi.access().hostname(configurator.hostname).port(configurator.port)
  private[this] val topicApi = TopicApi.access().hostname(configurator.hostname).port(configurator.port)

  private[this] def result[T](f: Future[T]): T = Await.result(f, 10 seconds)

  private[this] def assertNoMetricsForTopic(topicId: String): Unit = {
    CommonUtils.await(() => BeanChannel.local().topicMeters().asScala.count(_.topicName() == topicId) == 0,
                      java.time.Duration.ofSeconds(60))
  }

  @Test
  def testConnector(): Unit = {
    val topicName = methodName
    val topic = result(
      topicApi.add(
        TopicCreationRequest(name = Some(topicName),
                             brokerClusterName = None,
                             numberOfPartitions = None,
                             numberOfReplications = None)))
    val request = ConnectorCreationRequest(
      workerClusterName = None,
      className = Some(classOf[DumbSink].getName),
      columns = Seq.empty,
      topicNames = Seq(topic.id),
      numberOfTasks = Some(1),
      settings = Map.empty
    )

    val sink = result(connectorApi.add(request))

    sink.metrics.meters.size shouldBe 0

    result(connectorApi.start(sink.id))

    CommonUtils.await(() => {
      result(connectorApi.get(sink.id)).metrics.meters.nonEmpty
    }, java.time.Duration.ofSeconds(20))

    CommonUtils.await(() => {
      result(topicApi.get(topic.id)).metrics.meters.nonEmpty
    }, java.time.Duration.ofSeconds(20))

    result(connectorApi.stop(sink.id))

    CommonUtils.await(() => {
      result(connectorApi.get(sink.id)).metrics.meters.isEmpty
    }, java.time.Duration.ofSeconds(20))

    result(topicApi.delete(topic.id))

    assertNoMetricsForTopic(topic.id)
  }

  @Test
  def testPipeline(): Unit = {
    val topicName = methodName
    val topic = result(
      topicApi.add(
        TopicCreationRequest(name = Some(topicName),
                             brokerClusterName = None,
                             numberOfPartitions = None,
                             numberOfReplications = None)))
    val request = ConnectorCreationRequest(
      workerClusterName = None,
      className = Some(classOf[DumbSink].getName),
      columns = Seq.empty,
      topicNames = Seq(topic.id),
      numberOfTasks = Some(1),
      settings = Map.empty
    )

    val sink = result(connectorApi.add(request))

    val pipelineApi = PipelineApi.access().hostname(configurator.hostname).port(configurator.port)

    val pipeline = result(
      pipelineApi.add(
        PipelineApi.PipelineCreationRequest(
          name = CommonUtils.randomString(),
          workerClusterName = None,
          flows = Seq(
            Flow(
              from = topic.id,
              to = Seq(sink.id)
            ))
        )))
    pipeline.objects.filter(_.id == sink.id).head.metrics.meters.size shouldBe 0
    result(connectorApi.start(sink.id))

    // the connector is running so we should "see" the beans.
    CommonUtils.await(
      () => result(pipelineApi.get(pipeline.id)).objects.filter(_.id == sink.id).head.metrics.meters.nonEmpty,
      java.time.Duration.ofSeconds(20))

    result(connectorApi.stop(sink.id))

    // the connector is stopped so we should NOT "see" the beans.
    CommonUtils.await(
      () => result(pipelineApi.get(pipeline.id)).objects.filter(_.id == sink.id).head.metrics.meters.isEmpty,
      java.time.Duration.ofSeconds(20))
  }

  @Test
  def testTopicMeterInPerfSource(): Unit = {
    val topicName = CommonUtils.randomString()
    val topic = result(
      topicApi.add(
        TopicCreationRequest(name = Some(topicName),
                             brokerClusterName = None,
                             numberOfPartitions = None,
                             numberOfReplications = None)))
    val request = ConnectorCreationRequest(
      workerClusterName = None,
      className = Some("com.island.ohara.connector.perf.PerfSource"),
      columns = Seq.empty,
      topicNames = Seq(topic.id),
      numberOfTasks = Some(1),
      settings = Map(
        "perf.batch" -> "1",
        "perf.frequence" -> java.time.Duration.ofSeconds(1).toString
      )
    )

    val source = result(connectorApi.add(request))

    val pipelineApi = PipelineApi.access().hostname(configurator.hostname).port(configurator.port)

    val pipeline = result(
      pipelineApi.add(
        PipelineApi.PipelineCreationRequest(
          name = CommonUtils.randomString(),
          workerClusterName = None,
          flows = Seq(
            Flow(
              from = topic.id,
              to = Seq(source.id)
            ))
        )))
    pipeline.objects.filter(_.id == source.id).head.metrics.meters.size shouldBe 0
    result(connectorApi.start(source.id))

    // the connector is running so we should "see" the beans.
    CommonUtils.await(
      () => result(pipelineApi.get(pipeline.id)).objects.filter(_.id == source.id).head.metrics.meters.nonEmpty,
      java.time.Duration.ofSeconds(20))

    CommonUtils.await(
      () => result(pipelineApi.get(pipeline.id)).objects.filter(_.id == topic.id).head.metrics.meters.nonEmpty,
      java.time.Duration.ofSeconds(20))

    result(connectorApi.stop(source.id))

    // the connector is stopped so we should NOT "see" the beans.
    CommonUtils.await(
      () => result(pipelineApi.get(pipeline.id)).objects.filter(_.id == source.id).head.metrics.meters.isEmpty,
      java.time.Duration.ofSeconds(20))

    // remove topic
    result(topicApi.delete(topic.id))
    CommonUtils.await(() => !result(pipelineApi.get(pipeline.id)).objects.exists(_.id == topic.id),
                      java.time.Duration.ofSeconds(30))
    assertNoMetricsForTopic(topic.id)
  }

  @After
  def tearDown(): Unit = Releasable.close(configurator)
}
