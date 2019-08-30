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

package com.island.ohara.streams.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.island.ohara.common.json.JsonUtils;
import com.island.ohara.common.setting.SettingDef;
import com.island.ohara.common.setting.SettingDef.Type;
import com.island.ohara.common.util.VersionUtils;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * This is an helper class for getting / setting {@link com.island.ohara.common.setting.SettingDef}
 * for StreamApp.
 */
public final class StreamDefUtils {

  static final String CORE_GROUP = "core";

  /** This is the default configurations we will load into {@code StreamDefUtils}. */
  private static final AtomicInteger ORDER_COUNTER = new AtomicInteger(0);

  public static final SettingDef BROKER_CLUSTER_NAME_DEFINITION =
      SettingDef.builder()
          .key("broker.cluster.name")
          .group(CORE_GROUP)
          .orderInGroup(ORDER_COUNTER.getAndIncrement())
          .displayName("Broker cluster name")
          .documentation("the name of broker cluster used to transfer data for this streamapp")
          .valueType(Type.STRING)
          .internal()
          .build();

  public static final SettingDef BROKER_DEFINITION =
      SettingDef.builder()
          .key("servers")
          .group(CORE_GROUP)
          .orderInGroup(ORDER_COUNTER.getAndIncrement())
          .displayName("Broker list")
          .documentation("The broker list of current workspace")
          .valueType(Type.STRING)
          .internal()
          .build();

  public static final SettingDef IMAGE_NAME_DEFINITION =
      SettingDef.builder()
          .key("imageName")
          .group(CORE_GROUP)
          .orderInGroup(ORDER_COUNTER.getAndIncrement())
          .displayName("Image name")
          .documentation("The image name of this streamApp running with")
          .valueType(Type.STRING)
          .optional()
          // In manager, user cannot change the image name
          .readonly()
          .build();

  public static final SettingDef NAME_DEFINITION =
      SettingDef.builder()
          .key("name")
          .group(CORE_GROUP)
          .orderInGroup(ORDER_COUNTER.getAndIncrement())
          .displayName("StreamApp name")
          .documentation("The unique name of this streamApp")
          .valueType(Type.STRING)
          .optional()
          .build();

  public static final SettingDef GROUP_DEFINITION =
      SettingDef.builder()
          .key("group")
          .group(CORE_GROUP)
          .orderInGroup(ORDER_COUNTER.getAndIncrement())
          .displayName("StreamApp group")
          .internal()
          .documentation("The group name of this streamApp")
          .valueType(Type.STRING)
          .build();

  public static final SettingDef JAR_KEY_DEFINITION =
      SettingDef.builder()
          .key("jarKey")
          .group(CORE_GROUP)
          .orderInGroup(ORDER_COUNTER.getAndIncrement())
          .displayName("Jar primary key")
          .documentation("The jar key of this streamApp using")
          .readonly()
          .valueType(Type.JAR_KEY)
          .build();

  /** this field is used to store whole info for a jar file */
  public static final SettingDef JAR_INFO_DEFINITION =
      SettingDef.builder()
          .key("jarInfo")
          .group(CORE_GROUP)
          .orderInGroup(ORDER_COUNTER.getAndIncrement())
          .displayName("Jar Info")
          .documentation("The jar info of this streamApp using")
          .valueType(Type.STRING)
          .internal()
          .build();

  public static final SettingDef FROM_TOPIC_KEYS_DEFINITION =
      SettingDef.builder()
          .key("from")
          .group(CORE_GROUP)
          .orderInGroup(ORDER_COUNTER.getAndIncrement())
          .reference(SettingDef.Reference.TOPIC)
          .displayName("From topic of data consuming from")
          .documentation("The topic name of this streamApp should consume from")
          .valueType(Type.TOPIC_KEYS)
          .build();

  public static final SettingDef TO_TOPIC_KEYS_DEFINITION =
      SettingDef.builder()
          .key("to")
          .group(CORE_GROUP)
          .orderInGroup(ORDER_COUNTER.getAndIncrement())
          .reference(SettingDef.Reference.TOPIC)
          .displayName("To topic of data produce to")
          .documentation("The topic name of this streamApp should produce to")
          .valueType(Type.TOPIC_KEYS)
          .build();

  public static final SettingDef JMX_PORT_DEFINITION =
      SettingDef.builder()
          .key("jmxPort")
          .group(CORE_GROUP)
          .orderInGroup(ORDER_COUNTER.getAndIncrement())
          .displayName("JMX export port")
          .documentation("The port of this streamApp using to export jmx metrics")
          .valueType(Type.PORT)
          .optional()
          .build();

  public static final SettingDef INSTANCES_DEFINITION =
      SettingDef.builder()
          .key("instances")
          .group(CORE_GROUP)
          .orderInGroup(ORDER_COUNTER.getAndIncrement())
          .displayName("Instances")
          .documentation("The running container number of this streamApp")
          .valueType(Type.INT)
          .build();

  public static final SettingDef NODE_NAMES_DEFINITION =
      SettingDef.builder()
          .key("nodeNames")
          .group(CORE_GROUP)
          .orderInGroup(ORDER_COUNTER.getAndIncrement())
          .displayName("Node name list")
          .documentation("The used node name list of this streamApp")
          .internal()
          .valueType(Type.ARRAY)
          .build();

  public static final SettingDef EXACTLY_ONCE_DEFINITION =
      SettingDef.builder()
          .key("exactlyOnce")
          .group(CORE_GROUP)
          .orderInGroup(ORDER_COUNTER.getAndIncrement())
          .displayName("Enable exactly once")
          .documentation("Enable this streamApp to process each record exactly once")
          .readonly()
          .valueType(Type.BOOLEAN)
          .optional("false")
          .build();

  public static final SettingDef VERSION_DEFINITION =
      SettingDef.builder()
          .key("version")
          .group(CORE_GROUP)
          .orderInGroup(ORDER_COUNTER.getAndIncrement())
          .displayName("Version")
          .documentation("Version of streamApp")
          .readonly()
          .valueType(Type.STRING)
          .optional(VersionUtils.VERSION)
          .build();

  public static final SettingDef REVISION_DEFINITION =
      SettingDef.builder()
          .key("revision")
          .group(CORE_GROUP)
          .orderInGroup(ORDER_COUNTER.getAndIncrement())
          .displayName("Revision")
          .readonly()
          .documentation("Revision of streamApp")
          .valueType(Type.STRING)
          .optional(VersionUtils.REVISION)
          .build();

  public static final SettingDef AUTHOR_DEFINITION =
      SettingDef.builder()
          .key("author")
          .group(CORE_GROUP)
          .orderInGroup(ORDER_COUNTER.getAndIncrement())
          .displayName("Author")
          .readonly()
          .documentation("Author of streamApp")
          .valueType(Type.STRING)
          .optional(VersionUtils.USER)
          .build();

  public static final SettingDef TAGS_DEFINITION =
      SettingDef.builder()
          .key("tags")
          .group(CORE_GROUP)
          .orderInGroup(ORDER_COUNTER.getAndIncrement())
          .displayName("Tags")
          .documentation("Tags of streamApp")
          .valueType(Type.TAGS)
          // In manager, the tags field is for internal use
          .internal()
          .optional()
          .build();

  // this is the jar url definition that used in container start argument
  public static final SettingDef JAR_URL_DEFINITION =
      SettingDef.builder()
          .key("jarUrl")
          .group(CORE_GROUP)
          .orderInGroup(ORDER_COUNTER.getAndIncrement())
          .displayName("Jar url key")
          .documentation("The jar url that will be used in streamApp")
          .internal()
          .valueType(Type.STRING)
          .build();

  // this is the streamApp metric group definition
  public static final SettingDef STREAMAPP_METRIC_GROUP_DEFINITION =
      SettingDef.builder()
          .key("streamMetricGroup")
          .group(CORE_GROUP)
          .orderInGroup(ORDER_COUNTER.getAndIncrement())
          .optional("streamapp")
          .internal()
          .valueType(Type.STRING)
          .build();

  // this streamApp entry class name
  public static final SettingDef CLASS_NAME_DEFINITION =
      SettingDef.builder()
          .key("className")
          .group(CORE_GROUP)
          .orderInGroup(ORDER_COUNTER.getAndIncrement())
          .readonly()
          .internal()
          .valueType(Type.STRING)
          .build();

  /**
   * Load configDefs from default definitions.
   *
   * <p>This field is associated to a immutable map.
   */
  public static final List<SettingDef> DEFAULT =
      Arrays.stream(StreamDefUtils.class.getDeclaredFields())
          .filter(field -> field.getType().isAssignableFrom(SettingDef.class))
          .map(
              field -> {
                try {
                  return (SettingDef) field.get(new StreamDefUtils());
                } catch (IllegalAccessException e) {
                  throw new IllegalArgumentException("field is not able cast to SettingDef", e);
                }
              })
          .collect(Collectors.toList());

  public static String toJson(StreamDefinitions definitions) {
    return JsonUtils.toString(definitions.values());
  }

  public static StreamDefinitions ofJson(String json) {
    return new StreamDefinitions(
        JsonUtils.toObject(json, new TypeReference<List<SettingDef>>() {}));
  }

  // disable constructor
  private StreamDefUtils() {}
}
