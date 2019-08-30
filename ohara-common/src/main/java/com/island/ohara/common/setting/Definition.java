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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.island.ohara.common.json.JsonObject;
import com.island.ohara.common.json.JsonUtils;
import java.io.Serializable;
import java.util.List;

/**
 * The definition of a object which need metadata.
 *
 * <p>It should consist of a class name and SettingDef list.
 */
public final class Definition implements JsonObject, Serializable {
  private static final long serialVersionUID = 1L;

  // -------------------------------[key]-------------------------------//
  public static final String CLASS_NAME_KEY = "className";
  public static final String DEFINITIONS_KEY = "definitions";

  private final String className;
  private final List<SettingDef> definitions;

  // The only entry to create definition object
  public static Definition of(String className, List<SettingDef> definitions) {
    return new Definition(className, definitions);
  }

  @JsonCreator
  private Definition(
      @JsonProperty(CLASS_NAME_KEY) String className,
      @JsonProperty(DEFINITIONS_KEY) List<SettingDef> definitions) {
    this.className = className;
    this.definitions = definitions;
  }

  @JsonProperty(CLASS_NAME_KEY)
  public String className() {
    return className;
  }

  @JsonProperty(DEFINITIONS_KEY)
  public List<SettingDef> definitions() {
    return definitions;
  }

  @Override
  public String toString() {
    return toJsonString();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof Definition) return toJsonString().equals(((Definition) obj).toJsonString());
    return false;
  }

  @Override
  public int hashCode() {
    return toJsonString().hashCode();
  }

  public static Definition ofJson(String json) {
    return JsonUtils.toObject(json, new TypeReference<Definition>() {});
  }
}
