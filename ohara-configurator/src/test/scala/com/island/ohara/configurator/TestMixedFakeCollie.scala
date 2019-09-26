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

import com.island.ohara.client.configurator.v0.{BrokerApi, NodeApi, WorkerApi, ZookeeperApi}
import com.island.ohara.common.util.CommonUtils
import com.island.ohara.testing.WithBrokerWorker
import org.junit.Test
import org.scalatest.Matchers

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
  * Embedded mode with fake cluster.
  */
class TestMixedFakeCollie extends WithBrokerWorker with Matchers {

  @Test
  def test(): Unit = {
    val configurator = Configurator.builder.fake(testUtil().brokersConnProps(), testUtil().workersConnProps()).build()

    try {
      Await
        .result(BrokerApi.access.hostname(configurator.hostname).port(configurator.port).list(), 20 seconds)
        .size shouldBe 1

      Await
        .result(WorkerApi.access.hostname(configurator.hostname).port(configurator.port).list(), 20 seconds)
        .size shouldBe 1

      val nodes =
        Await.result(NodeApi.access.hostname(configurator.hostname).port(configurator.port).list(), 10 seconds)

      // embedded mode always add single node since embedded mode assign different client port to each thread and
      // our collie demands that all "processes" should use same port.
      nodes.size shouldBe 1

      // there is no zk cluster so we can't add bk cluster
      an[IllegalArgumentException] should be thrownBy Await.result(
        BrokerApi.access
          .hostname(configurator.hostname)
          .port(configurator.port)
          .request
          .name(CommonUtils.randomString(10))
          .nodeNames(nodes.map(_.name).toSet)
          .create(),
        20 seconds
      )

      val zk = Await.result(
        ZookeeperApi.access
          .hostname(configurator.hostname)
          .port(configurator.port)
          .request
          .name(CommonUtils.randomString(10))
          .nodeNames(nodes.map(_.name).toSet)
          .create(),
        20 seconds
      )
      Await
        .result(ZookeeperApi.access.hostname(configurator.hostname).port(configurator.port).start(zk.key), 20 seconds)

      val bk = Await.result(
        BrokerApi.access
          .hostname(configurator.hostname)
          .port(configurator.port)
          .request
          .name(CommonUtils.randomString(10))
          .zookeeperClusterKey(zk.key)
          .nodeNames(nodes.map(_.name).toSet)
          .create(),
        20 seconds
      )
      Await.result(BrokerApi.access.hostname(configurator.hostname).port(configurator.port).start(bk.key), 20 seconds)

      Await
        .result(BrokerApi.access.hostname(configurator.hostname).port(configurator.port).list(), 20 seconds)
        .size shouldBe 2

      Await.result(
        WorkerApi.access
          .hostname(configurator.hostname)
          .port(configurator.port)
          .request
          .name(CommonUtils.randomString(10))
          .brokerClusterName(bk.name)
          .nodeNames(nodes.map(_.name).toSet)
          .create(),
        20 seconds
      )

      Await
        .result(WorkerApi.access.hostname(configurator.hostname).port(configurator.port).list(), 20 seconds)
        .size shouldBe 2

    } finally configurator.close()
  }
}
