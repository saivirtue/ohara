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

import java.io.File

import com.island.ohara.client.configurator.v0.DefinitionApi.ServiceType
import com.island.ohara.client.configurator.v0.{DefinitionApi, FileInfoApi, NodeApi, StreamApi}
import com.island.ohara.common.rule.MediumTest
import com.island.ohara.common.util.{CommonUtils, Releasable}
import com.island.ohara.configurator.Configurator
import com.island.ohara.configurator.stream.DefinitionStreamApp
import org.junit.{After, Before, Test}
import org.scalatest.Matchers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
class TestDefinitionRoute extends MediumTest with Matchers {
  private[this] val configurator = Configurator.builder.fake(0, 0).build()
  private[this] val definitionApi = DefinitionApi.access.hostname(configurator.hostname).port(configurator.port)
  private[this] val fileAPi = FileInfoApi.access.hostname(configurator.hostname).port(configurator.port)

  private[this] val nodeNames: Set[String] = Set("n0", "n1")

  private[this] def result[T](f: Future[T]): T = Await.result(f, Duration("20 seconds"))

  @Before
  def setup(): Unit = {
    val nodeAccess = NodeApi.access.hostname(configurator.hostname).port(configurator.port)

    nodeNames.isEmpty shouldBe false

    nodeNames.foreach { n =>
      result(
        nodeAccess.request.hostname(n).port(22).user("user").password("password").create()
      )
    }

    result(nodeAccess.list()).size shouldBe nodeNames.size
  }

  @Test
  def fetchStreamDefinition(): Unit = {
    // without specify jar
    val definition = result(
      definitionApi.request.serviceType(ServiceType.Stream).imageName(StreamApi.IMAGE_NAME_DEFAULT).fetch())
    definition.definitions().size() should not be 0

    // with specific jar
    val jar = new File(
      CommonUtils.path(System.getProperty("user.dir"), "build", "libs", "ohara-definition-streamapp.jar"))
    // upload streamApp jar
    val jarInfo = result(fileAPi.request.file(jar).upload())
    val customDefinition = result(
      definitionApi.request
        .serviceType(ServiceType.Stream)
        .imageName(StreamApi.IMAGE_NAME_DEFAULT)
        .jarInfo(jarInfo)
        .fetch())

    customDefinition.className() shouldBe classOf[DefinitionStreamApp].getCanonicalName
    // we add some custom definitions, so it should not be default definitions
    customDefinition.definitions().size() should not be definition.definitions().size()
  }

  @After
  def tearDown(): Unit = Releasable.close(configurator)
}
