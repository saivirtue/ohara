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

package com.island.ohara.common.setting;

import com.island.ohara.common.rule.SmallTest;
import com.island.ohara.common.util.CommonUtils;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Assert;
import org.junit.Test;

public class TestDefinition extends SmallTest {

  @Test
  public void testJsonParser() {
    List<SettingDef> definitions =
        Stream.of(
                SettingDef.builder()
                    .key(CommonUtils.randomString())
                    .valueType(SettingDef.Type.STRING)
                    .build(),
                SettingDef.builder()
                    .key(CommonUtils.randomString())
                    .valueType(SettingDef.Type.ARRAY)
                    .build())
            .collect(Collectors.toList());

    Definition definition = Definition.of("fake_class", definitions);
    Assert.assertEquals(definition, Definition.ofJson(definition.toString()));
  }
}
