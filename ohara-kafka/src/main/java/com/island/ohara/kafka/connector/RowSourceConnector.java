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

package com.island.ohara.kafka.connector;

import static com.island.ohara.kafka.connector.ConnectorUtil.VERSION;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.kafka.common.config.Config;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.ConnectorContext;
import org.apache.kafka.connect.connector.Task;
import org.apache.kafka.connect.source.SourceConnector;

/** A wrap to SourceConnector. Currently, only Task is replaced by ohara object - RowSourceTask */
public abstract class RowSourceConnector extends SourceConnector {

  /**
   * Returns the RowSourceTask implementation for this Connector.
   *
   * @return a RowSourceTask class
   */
  protected abstract Class<? extends RowSourceTask> _taskClass();

  /**
   * Return the configs for source task.
   *
   * @param maxTasks number of tasks for this connector
   * @return a seq from configs
   */
  protected abstract List<TaskConfig> _taskConfigs(int maxTasks);

  /**
   * Start this Connector. This method will only be called on a clean Connector, i.e. it has either
   * just been instantiated and initialized or _stop() has been invoked.
   *
   * @param config configuration settings
   */
  protected abstract void _start(TaskConfig config);

  /** stop this connector */
  protected abstract void _stop();

  /**
   * Define the configuration for the connector. TODO: wrap ConfigDef ... by chia
   *
   * @return The ConfigDef for this connector.
   */
  protected ConfigDef _config() {
    return new ConfigDef();
  };

  /**
   * Get the version from this connector.
   *
   * @return the version, formatted as a String
   */
  protected String _version() {
    return VERSION;
  };

  // -------------------------------------------------[WRAPPED]-------------------------------------------------//

  @Override
  public final List<Map<String, String>> taskConfigs(int maxTasks) {
    return _taskConfigs(maxTasks).stream().map(ConnectorUtil::toMap).collect(Collectors.toList());
  }

  @Override
  public final Class<? extends Task> taskClass() {
    return _taskClass();
  }

  @Override
  public final void start(Map<String, String> props) {
    _start(ConnectorUtil.toTaskConfig(props));
  }

  @Override
  public final void stop() {
    _stop();
  }

  @Override
  public final ConfigDef config() {
    return _config();
  }

  @Override
  public final String version() {
    return _version();
  }
  // -------------------------------------------------[UN-OVERRIDE]-------------------------------------------------//
  @Override
  public final void initialize(ConnectorContext ctx) {
    super.initialize(ctx);
  }

  @Override
  public final void initialize(ConnectorContext ctx, List<Map<String, String>> taskConfigs) {
    super.initialize(ctx, taskConfigs);
  }

  @Override
  public final void reconfigure(Map<String, String> props) {
    super.reconfigure(props);
  }

  @Override
  public final Config validate(Map<String, String> connectorConfigs) {
    return super.validate(connectorConfigs);
  }
}
