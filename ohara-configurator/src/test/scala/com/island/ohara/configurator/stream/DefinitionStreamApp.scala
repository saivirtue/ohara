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

package com.island.ohara.configurator.stream
import com.island.ohara.common.data.Row
import com.island.ohara.common.setting.SettingDef
import com.island.ohara.common.util.CommonUtils
import com.island.ohara.streams.config.StreamDefinitions
import com.island.ohara.streams.{OStream, StreamApp}
import scala.collection.JavaConverters._

private[configurator] class DefinitionStreamApp extends StreamApp {

  override def config(): StreamDefinitions = {
    StreamDefinitions.withAll(
      Seq(
        SettingDef.builder().key(CommonUtils.randomString).valueType(SettingDef.Type.STRING).build(),
        SettingDef.builder().key(CommonUtils.randomString).valueType(SettingDef.Type.ARRAY).build(),
        SettingDef.builder().key(CommonUtils.randomString).valueType(SettingDef.Type.INT).build()
      ).asJava)
  }

  override def start(ostream: OStream[Row], streamDefinitions: StreamDefinitions): Unit = ostream.start()
}
