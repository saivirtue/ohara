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
import com.island.ohara.common.util.Releasable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;

/**
 * a simple wrap from kafka producer.
 *
 * @param <K> key type
 * @param <V> value type
 */
public interface Producer<K, V> extends Releasable {

  /**
   * create a sender used to send a record to brokers
   *
   * @return a sender
   */
  Sender<K, V> sender();

  /** flush all on-the-flight data. */
  void flush();

  static Builder builder() {
    return new Builder();
  }

  class Builder {
    private String connectionProps;
    // default noAcks
    private short numberOfAcks = 0;

    private Builder() {
      // no nothing
    }

    public Builder connectionProps(String connectionProps) {
      this.connectionProps = connectionProps;
      return this;
    }

    public Builder noAcks() {
      this.numberOfAcks = 0;
      return this;
    }

    public Builder allAcks() {
      this.numberOfAcks = -1;
      return this;
    }

    /**
     * Used to convert ohara row to byte array. It is a private class since ohara producer will
     * instantiate one and pass it to kafka producer. Hence, no dynamical call will happen in kafka
     * producer. The access exception won't be caused.
     *
     * @param serializer ohara serializer
     * @param <T> object type
     * @return a wrapper from kafka serializer
     */
    private static <T> org.apache.kafka.common.serialization.Serializer<T> wrap(
        Serializer<T> serializer) {
      return new org.apache.kafka.common.serialization.Serializer<T>() {

        @Override
        public void configure(Map<String, ?> configs, boolean isKey) {
          // do nothing
        }

        @Override
        public byte[] serialize(String topic, T data) {
          return data == null ? null : serializer.to(data);
        }

        @Override
        public void close() {
          // do nothing
        }
      };
    }
    /**
     * @param <K> key type
     * @param <V> value type
     */
    public <K, V> Producer<K, V> build(Serializer<K> keySerializer, Serializer<V> valueSerializer) {
      Objects.requireNonNull(connectionProps);
      return new Producer<K, V>() {

        private Properties getProducerConfig() {
          Properties props = new Properties();
          props.setProperty(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, connectionProps);
          props.setProperty(ProducerConfig.ACKS_CONFIG, String.valueOf(numberOfAcks));
          return props;
        }

        private final KafkaProducer<K, V> producer =
            new KafkaProducer<>(getProducerConfig(), wrap(keySerializer), wrap(valueSerializer));

        @Override
        public final Sender<K, V> sender() {
          return new Sender<K, V>() {

            @Override
            protected void doSend(String topic, Handler<RecordMetadata> handler) {
              ProducerRecord<K, V> record =
                  new ProducerRecord<>(
                      topic,
                      partition().map(Integer::new).orElse(null),
                      timestamp().map(Long::new).orElse(null),
                      key().orElse(null),
                      value().orElse(null),
                      headers()
                          .stream()
                          .map(Builder.this::toKafkaHeader)
                          .collect(Collectors.toList()));

              producer.send(
                  record,
                  (metadata, exception) -> {
                    if (metadata == null && exception == null)
                      handler.onFailure(
                          new IllegalStateException(
                              "no meta and exception from kafka producer...It should be impossible"));
                    if (metadata != null && exception != null)
                      handler.onFailure(
                          new IllegalStateException(
                              "Both meta and exception from kafka producer...It should be impossible"));
                    if (metadata != null)
                      handler.onSuccess(
                          new RecordMetadata(
                              metadata.topic(),
                              metadata.partition(),
                              metadata.offset(),
                              metadata.timestamp(),
                              metadata.serializedKeySize(),
                              metadata.serializedValueSize()));
                    if (exception != null) handler.onFailure(exception);
                  });
            }
          };
        }

        @Override
        public void flush() {
          producer.flush();
        }

        @Override
        public void close() {
          producer.close();
        }
      };
    }

    private org.apache.kafka.common.header.Header toKafkaHeader(Header header) {
      return new org.apache.kafka.common.header.Header() {
        @Override
        public String key() {
          return header.key();
        }

        @Override
        public byte[] value() {
          return header.value();
        }
      };
    }
  }

  /**
   * a fluent-style sender. kafak.ProducerRecord has many fields and most from them are nullable. It
   * makes kafak.ProducerRecord's constructor complicated. This class has fluent-style methods
   * helping user to fill the fields they have.
   */
  abstract class Sender<K, V> {
    private Integer partition = null;
    private List<Header> headers = Collections.emptyList();
    private K key = null;
    private V value = null;
    private Long timestamp = null;

    private Sender() {
      // do nothing
    }

    Optional<Integer> partition() {
      return Optional.ofNullable(partition);
    }

    List<Header> headers() {
      return headers;
    }

    Optional<K> key() {
      return Optional.ofNullable(key);
    }

    Optional<V> value() {
      return Optional.ofNullable(value);
    }

    Optional<Long> timestamp() {
      return Optional.ofNullable(timestamp);
    }

    public Sender<K, V> partition(int partition) {
      this.partition = partition;
      return this;
    }

    public Sender<K, V> header(Header header) {
      this.headers = Collections.singletonList(header);
      return this;
    }

    public Sender<K, V> headers(List<Header> headers) {
      this.headers = headers;
      return this;
    }

    public Sender<K, V> key(K key) {
      this.key = key;
      return this;
    }

    public Sender<K, V> value(V value) {
      this.value = value;
      return this;
    }

    public Sender<K, V> timestamp(long timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    /**
     * start to send the data in background. Noted: you should check the returned future to handle
     * the exception or result
     *
     * @return an async thread processing the request
     */
    public Future<RecordMetadata> send(String topic) {

      CompletableFuture<RecordMetadata> completableFuture = new CompletableFuture<>();
      send(
          topic,
          new Handler<RecordMetadata>() {

            @Override
            public void onFailure(Exception e) {
              completableFuture.completeExceptionally(e);
            }

            @Override
            public void onSuccess(RecordMetadata o) {
              completableFuture.complete(o);
            }
          });

      return completableFuture;
    }

    /**
     * send the record to brokers with callback
     *
     * @param handler invoked after the record is completed or failed
     */
    public void send(String topic, Handler<RecordMetadata> handler) {
      doSend(topic, handler);
    }

    protected abstract void doSend(String topic, Handler<RecordMetadata> handler);
  }

  interface Handler<T> {
    void onFailure(Exception e);

    void onSuccess(T t);
  }

  /** wrap from kafka RecordMetadata; */
  class RecordMetadata {
    private final String topic;
    private final int partition;
    private final long offset;
    private final long timestamp;
    private final int serializedKeySize;
    private final int serializedValueSize;

    private RecordMetadata(
        String topic,
        int partition,
        long offset,
        long timestamp,
        int serializedKeySize,
        int serializedValueSize) {
      this.topic = topic;
      this.partition = partition;
      this.offset = offset;
      this.timestamp = timestamp;
      this.serializedKeySize = serializedKeySize;
      this.serializedValueSize = serializedValueSize;
    }

    public String topic() {
      return topic;
    }

    public int partition() {
      return partition;
    }

    public long offset() {
      return offset;
    }

    public long timestamp() {
      return timestamp;
    }

    public int serializedKeySize() {
      return serializedKeySize;
    }

    public int serializedValueSize() {
      return serializedValueSize;
    }
  }
}
