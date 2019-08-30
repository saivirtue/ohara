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

import com.island.ohara.common.rule.SmallTest
import com.island.ohara.common.util.CommonUtils
import org.junit.Test
import org.scalatest.Matchers

import scala.concurrent.ExecutionContext.Implicits.global

class TestDefinitionApi extends SmallTest with Matchers {

  private val api = DefinitionApi.access.hostname(CommonUtils.randomString(5)).port(CommonUtils.availablePort()).request

  @Test
  def emptyImageName(): Unit = an[IllegalArgumentException] should be thrownBy api.imageName("").fetch()

  @Test
  def nullImageName(): Unit = an[NullPointerException] should be thrownBy api.imageName(null).fetch()

  @Test
  def nullServiceType(): Unit = an[NullPointerException] should be thrownBy api.serviceType(null).fetch()

  @Test
  def nullJarInfo(): Unit = an[NullPointerException] should be thrownBy api.jarInfo(null).fetch()
}
