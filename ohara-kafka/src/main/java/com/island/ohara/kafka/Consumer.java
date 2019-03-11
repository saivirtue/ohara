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

package com.island.ohara.kafka;

import com.island.ohara.common.data.Serializer;
import com.island.ohara.common.util.CommonUtil;
import com.island.ohara.common.util.Releasable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.serialization.Deserializer;

/**
 * A wrap of kafka consumer.
 *
 * @param <K> key type
 * @param <V> value type
 */
public interface Consumer<K, V> extends Releasable {

  /**
   * poll the data from subscribed topics
   *
   * @param timeout waiting time
   * @return records
   */
  List<Record<K, V>> poll(Duration timeout);

  /**
   * Overloading poll method
   *
   * @param timeout waiting time
   * @param expectedSize expected size
   * @return Records
   */
  default List<Record<K, V>> poll(Duration timeout, int expectedSize) {
    return poll(timeout, expectedSize, () -> false);
  }

  default List<Record<K, V>> poll(Duration timeout, int expectedSize, Supplier<Boolean> stop) {
    return poll(timeout, expectedSize, stop, Function.identity());
  }

  default List<Record<K, V>> poll(
      Duration timeout, int expectedSize, Function<List<Record<K, V>>, List<Record<K, V>>> filter) {
    return poll(timeout, expectedSize, () -> false, filter);
  }

  /**
   * It accept another condition - expected size from records. Somethins it is helpful if you
   * already know the number from records which should be returned.
   *
   * @param timeout timeout
   * @param expectedSize the number from records should be returned
   * @param stop supply a single to stop the internal loop
   * @param filter data filter
   * @return records
   */
  default List<Record<K, V>> poll(
      Duration timeout,
      int expectedSize,
      Supplier<Boolean> stop,
      Function<List<Record<K, V>>, List<Record<K, V>>> filter) {

    List<Record<K, V>> list;
    if (expectedSize == Integer.MAX_VALUE) list = new ArrayList<>();
    else list = new ArrayList<>(expectedSize);

    long endtime = CommonUtil.current() + timeout.toMillis();
    long ramaining = endtime - CommonUtil.current();

    while (!stop.get() && list.size() < expectedSize && ramaining > 0) {
      list.addAll(filter.apply(poll(Duration.ofMillis(ramaining))));
      ramaining = endtime - CommonUtil.current();
    }
    return list;
  }

  /** @return the topic names subscribed by this consumer */
  Set<String> subscription();

  /** break the poll right now. */
  void wakeup();

  static Builder builder() {
    return new Builder();
  }

  class Builder {

    private OffsetResetStrategy fromBegin = OffsetResetStrategy.LATEST;
    private List<String> topicNames;
    private String groupId = String.format("ohara-consumer-%s", CommonUtil.uuid());
    private String connectionProps;

    private Builder() {
      // do nothing
    }

    /**
     * receive all un-deleted message from subscribed topics
     *
     * @return this builder
     */
    public Builder offsetFromBegin() {
      this.fromBegin = OffsetResetStrategy.EARLIEST;
      return this;
    }

    /**
     * receive the messages just after the last one
     *
     * @return this builder
     */
    public Builder offsetAfterLatest() {
      this.fromBegin = OffsetResetStrategy.LATEST;
      return this;
    }

    /**
     * @param topicName the topic you want to subscribe
     * @return this builder
     */
    public Builder topicName(String topicName) {
      this.topicNames = Collections.singletonList(topicName);
      return this;
    }

    /**
     * @param topicNames the topics you want to subscribe
     * @return this builder
     */
    public Builder topicNames(List<String> topicNames) {
      this.topicNames = topicNames;
      return this;
    }

    public Builder groupId(String groupId) {
      this.groupId = groupId;
      return this;
    }

    public Builder connectionProps(String connectionProps) {
      this.connectionProps = connectionProps;
      return this;
    }

    /**
     * Used to convert byte array to ohara row. It is a private class since ohara consumer will
     * instantiate one and pass it to kafka consumer. Hence, no dynamical call will happen in kafka
     * consumer. The access exception won't be caused.
     *
     * @param serializer ohara serializer
     * @return a wrapper from kafka deserializer
     */
    private static <T> Deserializer<T> wrap(Serializer<T> serializer) {
      return new org.apache.kafka.common.serialization.Deserializer<T>() {

        @Override
        public void configure(Map<String, ?> configs, boolean isKey) {
          // do nothing
        }

        @Override
        public T deserialize(String topic, byte[] data) {
          return data == null ? null : serializer.from(data);
        }

        @Override
        public void close() {
          // do nothing
        }
      };
    }

    public <K, V> Consumer<K, V> build(Serializer<K> keySerializer, Serializer<V> valueSerializer) {
      Objects.requireNonNull(topicNames);
      if (topicNames.isEmpty()) throw new IllegalArgumentException("Topics list is empty");
      Objects.requireNonNull(groupId);
      Objects.requireNonNull(connectionProps);

      Properties consumerConfig = new Properties();

      consumerConfig.setProperty(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, connectionProps);
      consumerConfig.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupId);
      // kafka demand us to pass lowe case words...
      consumerConfig.setProperty(
          ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, fromBegin.name().toLowerCase());

      KafkaConsumer<K, V> kafkaConsumer =
          new KafkaConsumer<>(consumerConfig, wrap(keySerializer), wrap(valueSerializer));

      kafkaConsumer.subscribe(topicNames);

      return new Consumer<K, V>() {
        private ConsumerRecords<K, V> firstPoll = kafkaConsumer.poll(0);

        @Override
        public void close() {
          kafkaConsumer.close();
        }

        @Override
        public List<Record<K, V>> poll(Duration timeout) {

          ConsumerRecords<K, V> r;
          if (firstPoll == null || firstPoll.isEmpty()) r = kafkaConsumer.poll(timeout.toMillis());
          else {
            r = firstPoll;
            firstPoll = null;
          }

          if (r == null || r.isEmpty()) return Collections.emptyList();
          else
            return StreamSupport.stream(
                    Spliterators.spliteratorUnknownSize(r.iterator(), Spliterator.ORDERED), false)
                .map(
                    cr ->
                        new Record<>(
                            cr.topic(),
                            Optional.ofNullable(cr.headers())
                                .map(
                                    headers ->
                                        StreamSupport.stream(headers.spliterator(), false)
                                            .map(header -> new Header(header.key(), header.value()))
                                            .collect(Collectors.toList()))
                                .orElse(Collections.emptyList()),
                            cr.key(),
                            cr.value()))
                .collect(Collectors.toList());
        }

        @Override
        public Set<String> subscription() {
          return Collections.unmodifiableSet(kafkaConsumer.subscription());
        }

        @Override
        public void wakeup() {
          kafkaConsumer.wakeup();
        }
      };
    }
  }

  /**
   * a scala wrap from kafka's consumer record.
   *
   * @param <K> K key type
   * @param <V> V value type
   */
  class Record<K, V> {
    private final String topic;
    private final List<Header> headers;
    private final K key;
    private final V value;

    /**
     * @param topic topic name
     * @param key key (nullable)
     * @param value value
     */
    private Record(String topic, List<Header> headers, K key, V value) {
      this.topic = topic;
      this.headers = Collections.unmodifiableList(headers);
      this.key = key;
      this.value = value;
    }

    public String topic() {
      return topic;
    }

    public List<Header> headers() {
      return headers;
    }

    public Optional<K> key() {
      return Optional.ofNullable(key);
    }

    public Optional<V> value() {
      return Optional.ofNullable(value);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Record<?, ?> that = (Record<?, ?>) o;
      return Objects.equals(topic, that.topic)
          && CommonUtil.equals(headers, that.headers)
          && Objects.equals(key, that.key)
          && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(topic, headers, key, value);
    }

    @Override
    public String toString() {
      return new ToStringBuilder(this)
          .append("topic", topic)
          .append("headers", headers)
          .append("key", key)
          .append("value", value)
          .toString();
    }
  }
}
