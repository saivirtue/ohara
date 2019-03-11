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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.connect.sink.SinkRecord;
import org.apache.kafka.connect.sink.SinkTask;
import org.apache.kafka.connect.sink.SinkTaskContext;

/**
 * A wrap to Kafka SinkTask. It used to convert the Kafka SinkRecord to ohara RowSinkRecord. Ohara
 * developers should extend this class rather than kafka SinkTask in order to let the conversion
 * from SinkRecord to RowSinkRecord work automatically.
 */
public abstract class RowSinkTask extends SinkTask {

  /**
   * Start the Task. This should handle any configuration parsing and one-time setup from the task.
   *
   * @param config initial configuration
   */
  protected abstract void _start(TaskConfig config);

  /**
   * Perform any cleanup to stop this task. In SinkTasks, this method is invoked only once
   * outstanding calls to other methods have completed (e.g., _put() has returned) and a final
   * flush() and offset commit has completed. Implementations from this method should only need to
   * perform final cleanup operations, such as closing network connections to the sink system.
   */
  protected abstract void _stop();

  /**
   * Put the table record in the sink. Usually this should send the records to the sink
   * asynchronously and immediately return.
   *
   * @param records table record
   */
  protected abstract void _put(List<RowSinkRecord> records);

  /**
   * Get the version from this task. Usually this should be the same as the corresponding Connector
   * class's version.
   *
   * @return the version, formatted as a String
   */
  protected String _version() {
    return VERSION;
  };

  /**
   * The SinkTask use this method to create writers for newly assigned partitions in case from
   * partition rebalance. This method will be called after partition re-assignment completes and
   * before the SinkTask starts fetching data. Note that any errors raised from this method will
   * cause the task to stop.
   *
   * @param partitions The list from partitions that are now assigned to the task (may include
   *     partitions previously assigned to the task)
   */
  protected void _open(List<TopicPartition> partitions) {
    // do nothing
  };

  /**
   * The SinkTask use this method to close writers for partitions that are no longer assigned to the
   * SinkTask. This method will be called before a rebalance operation starts and after the SinkTask
   * stops fetching data. After being closed, Connect will not write any records to the task until a
   * new set from partitions has been opened. Note that any errors raised from this method will
   * cause the task to stop.
   *
   * @param partitions The list from partitions that should be closed
   */
  protected void _close(List<TopicPartition> partitions) {
    // do nothing
  };

  /**
   * Pre-commit hook invoked prior to an offset commit.
   *
   * <p>The default implementation simply return the offsets and is thus able to assume all offsets
   * are safe to commit.
   *
   * @param offsets the current offset state as from the last call to _put, provided for convenience
   *     but could also be determined by tracking all offsets included in the RowSourceRecord's
   *     passed to _put.
   * @return an empty map if Connect-managed offset commit is not desired, otherwise a map from
   *     offsets by topic-partition that are safe to commit.
   */
  protected Map<TopicPartition, TopicOffset> _preCommit(Map<TopicPartition, TopicOffset> offsets) {
    return offsets;
  };

  protected RowSinkContext rowContext;
  // -------------------------------------------------[WRAPPED]-------------------------------------------------//

  @Override
  public final void put(Collection<SinkRecord> records) {

    if (records == null) _put(Collections.emptyList());
    else _put(records.stream().map(RowSinkRecord::of).collect(Collectors.toList()));
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
  public final String version() {
    return _version();
  }

  @Override
  public final void open(Collection<org.apache.kafka.common.TopicPartition> partitions) {

    _open(
        partitions
            .stream()
            .map(p -> new TopicPartition(p.topic(), (p.partition())))
            .collect(Collectors.toList()));
  }

  @Override
  public final void close(Collection<org.apache.kafka.common.TopicPartition> partitions) {
    _close(
        partitions
            .stream()
            .map(p -> new TopicPartition(p.topic(), (p.partition())))
            .collect(Collectors.toList()));
  }

  @Override
  public final Map<org.apache.kafka.common.TopicPartition, OffsetAndMetadata> preCommit(
      Map<org.apache.kafka.common.TopicPartition, OffsetAndMetadata> currentOffsets) {

    return _preCommit(
            currentOffsets
                .entrySet()
                .stream()
                .collect(
                    Collectors.toMap(
                        x -> new TopicPartition(x.getKey().topic(), x.getKey().partition()),
                        x -> new TopicOffset(x.getValue().metadata(), x.getValue().offset()))))
        .entrySet()
        .stream()
        .collect(
            Collectors.toMap(
                x ->
                    new org.apache.kafka.common.TopicPartition(
                        x.getKey().topic(), x.getKey().partition()),
                x -> new OffsetAndMetadata(x.getValue().offset(), x.getValue().metadata())));
  }
  // -------------------------------------------------[UN-OVERRIDE]-------------------------------------------------//

  @Override
  public final void initialize(SinkTaskContext context) {
    super.initialize(context);
    rowContext = RowSinkContext.toRowSinkContext(context);
  }

  @SuppressWarnings({
    "deprecation",
    "kafka had deprecated this method but it still allow developer to override it. We forbrid the inherance now"
  })
  @Override
  public final void onPartitionsAssigned(
      Collection<org.apache.kafka.common.TopicPartition> partitions) {
    super.onPartitionsAssigned(partitions);
  }

  @SuppressWarnings({
    "deprecation",
    "kafka had deprecated this method but it still allow developer to override it. We forbrid the inherance now"
  })
  @Override
  public final void onPartitionsRevoked(
      Collection<org.apache.kafka.common.TopicPartition> partitions) {
    super.onPartitionsRevoked(partitions);
  }

  @Override
  public final void flush(
      Map<org.apache.kafka.common.TopicPartition, OffsetAndMetadata> currentOffsets) {
    // this API in connector is embarrassing since it is a part from default implementation from
    // preCommit...
  }
}
