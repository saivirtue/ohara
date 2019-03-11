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

package com.island.ohara.common.data;

import com.island.ohara.common.util.ByteUtil;
import java.io.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang3.ArrayUtils;

/**
 * Used to convert a T object to V NOTED: the impl should not be an inner/anonymous class since
 * Store will use the reflection to create the object. The dynamical call to inner/anonymous class
 * is fraught with risks.
 *
 * @param <T> data type
 */
public interface Serializer<T> {

  /**
   * Convert the object to a serializable type
   *
   * @param obj object
   * @return a serializable type
   */
  byte[] to(T obj);

  /**
   * Convert the serialized data to object
   *
   * @param bytes serialized data
   * @return object
   */
  T from(byte[] bytes);

  Serializer<byte[]> BYTES =
      new Serializer<byte[]>() {
        @Override
        public byte[] to(byte[] obj) {
          return obj;
        }

        @Override
        public byte[] from(byte[] bytes) {
          return bytes;
        }
      };

  Serializer<Boolean> BOOLEAN =
      new Serializer<Boolean>() {
        @Override
        public byte[] to(Boolean obj) {
          return ByteUtil.toBytes(obj);
        }

        @Override
        public Boolean from(byte[] bytes) {
          return ByteUtil.toBoolean(bytes);
        }
      };

  Serializer<Byte> BYTE =
      new Serializer<Byte>() {
        @Override
        public byte[] to(Byte obj) {
          return new byte[] {obj};
        }

        @Override
        public Byte from(byte[] bytes) {
          return bytes[0];
        }
      };

  Serializer<Short> SHORT =
      new Serializer<Short>() {
        @Override
        public byte[] to(Short obj) {
          return ByteUtil.toBytes(obj);
        }

        @Override
        public Short from(byte[] bytes) {
          return ByteUtil.toShort(bytes);
        }
      };

  Serializer<Integer> INT =
      new Serializer<Integer>() {
        @Override
        public byte[] to(Integer obj) {
          return ByteUtil.toBytes(obj);
        }

        @Override
        public Integer from(byte[] bytes) {
          return ByteUtil.toInt(bytes);
        }
      };

  Serializer<Long> LONG =
      new Serializer<Long>() {
        @Override
        public byte[] to(Long obj) {
          return ByteUtil.toBytes(obj);
        }

        @Override
        public Long from(byte[] bytes) {
          return ByteUtil.toLong(bytes);
        }
      };

  Serializer<Float> FLOAT =
      new Serializer<Float>() {
        @Override
        public byte[] to(Float obj) {
          return ByteUtil.toBytes(obj);
        }

        @Override
        public Float from(byte[] bytes) {
          return ByteUtil.toFloat(bytes);
        }
      };

  Serializer<Double> DOUBLE =
      new Serializer<Double>() {
        @Override
        public byte[] to(Double obj) {
          return ByteUtil.toBytes(obj);
        }

        @Override
        public Double from(byte[] bytes) {
          return ByteUtil.toDouble(bytes);
        }
      };

  Serializer<String> STRING =
      new Serializer<String>() {
        @Override
        public byte[] to(String obj) {
          return ByteUtil.toBytes(obj);
        }

        @Override
        public String from(byte[] bytes) {
          return ByteUtil.toString(bytes);
        }
      };

  Serializer<Row> ROW =
      new Serializer<Row>() {
        @Override
        public byte[] to(Row row) {
          try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            output.write(0);
            toV0(output, row);
            output.flush();
            return output.toByteArray();
          } catch (Exception e) {
            throw new IllegalArgumentException(e);
          }
        }

        /**
         * cell count from row (4 bytes) cell name length (2 bytes) | cell name | cell value type (2
         * byte) | cell value length (2 bytes) | cell value cell name length (2 bytes) | cell name |
         * cell value type (2 byte) | cell value length (2 bytes) | cell value tag count (2 bytes)
         * tag length (2 bytes) | tag bytes tag length (2 bytes) | tag bytes
         *
         * @param row row
         * @param output output
         */
        @SuppressWarnings("unchecked")
        private void toV0(ByteArrayOutputStream output, Row row) throws IOException {
          output.write(INT.to(row.cells().size()));
          row.cells()
              .stream()
              .forEach(
                  c -> {
                    // we have got to cast Cell<?> to Cell<object>. Otherwise, we can't obey CAP#1
                    Cell<Object> cell = (Cell<Object>) c;
                    try {
                      byte[] nameBytes = STRING.to(cell.name());
                      final byte[] valueBytes;
                      DataType type = DataType.from(cell.value());
                      switch (type) {
                        case BYTES:
                          valueBytes = BYTES.to((byte[]) cell.value());
                          break;
                        case BOOLEAN:
                          valueBytes = BOOLEAN.to((boolean) cell.value());
                          break;
                        case BYTE:
                          valueBytes = BYTE.to((byte) cell.value());
                          break;
                        case SHORT:
                          valueBytes = SHORT.to((short) cell.value());
                          break;
                        case INT:
                          valueBytes = INT.to((int) cell.value());
                          break;
                        case LONG:
                          valueBytes = LONG.to((long) cell.value());
                          break;
                        case FLOAT:
                          valueBytes = FLOAT.to((float) cell.value());
                          break;
                        case DOUBLE:
                          valueBytes = DOUBLE.to((double) cell.value());
                          break;
                        case STRING:
                          valueBytes = STRING.to((String) cell.value());
                          break;
                        case ROW:
                          valueBytes = ROW.to((Row) cell.value());
                          break;
                        case OBJECT:
                          valueBytes = OBJECT.to(cell.value());
                          break;
                        default:
                          throw new UnsupportedClassVersionError(type.getClass().getName());
                      }
                      if (nameBytes.length > Short.MAX_VALUE)
                        throw new IllegalArgumentException(
                            "the max size from name is "
                                + Short.MAX_VALUE
                                + " current:"
                                + nameBytes.length);
                      if (valueBytes.length > Short.MAX_VALUE)
                        throw new IllegalArgumentException(
                            "the max size from value is "
                                + Short.MAX_VALUE
                                + " current:"
                                + valueBytes.length);
                      // noted: the (int) length is converted to short type.
                      output.write(SHORT.to((short) nameBytes.length));
                      output.write(nameBytes);
                      output.write(SHORT.to(type.order));
                      // noted: the (int) length is converted to short type.
                      output.write(SHORT.to((short) valueBytes.length));
                      output.write(valueBytes);
                    } catch (IOException e) {
                      throw new IllegalArgumentException(e);
                    }
                  });

          // process tag
          // noted: the (int) length is converted to short type.
          output.write(SHORT.to((short) row.tags().size()));
          row.tags()
              .forEach(
                  tag -> {
                    try {
                      byte[] tagBytes = STRING.to(tag);
                      if (tagBytes.length > Short.MAX_VALUE)
                        throw new IllegalArgumentException(
                            "the max size from tag is "
                                + Short.MAX_VALUE
                                + " current:"
                                + tagBytes.length);
                      // noted: the (int) length is converted to short type.
                      output.write(SHORT.to((short) tagBytes.length));
                      output.write(tagBytes);
                    } catch (IOException e) {
                      throw new IllegalArgumentException(e);
                    }
                  });
        }

        @Override
        public Row from(byte[] bytes) {
          try (InputStream input = new ByteArrayInputStream(bytes)) {
            int version = input.read();
            switch (version) {
              case 0:
                return fromV0(input);
              default:
                throw new UnsupportedOperationException("Unsupported version:" + version);
            }
          } catch (Exception e) {
            throw new IllegalArgumentException(e);
          }
        }

        private byte[] forceRead(InputStream input, int len) {
          if (len == 0) return ArrayUtils.EMPTY_BYTE_ARRAY;
          else if (len < 0) throw new IllegalStateException(len + " should be bigger than zero");
          else {
            int remaining = len;
            byte[] buf = new byte[len];
            while (remaining != 0) {
              try {
                int rval = input.read(buf, buf.length - remaining, remaining);
                if (rval < 0)
                  throw new IllegalStateException(
                      "required " + len + " but actual " + (len - remaining) + " bytes");
                if (rval > remaining)
                  throw new IllegalStateException(
                      "ask " + remaining + " bytes but actual " + rval + " bytes");
                remaining -= rval;
              } catch (Throwable e) {
                throw new IllegalStateException(e);
              }
            }
            return buf;
          }
        }

        private Row fromV0(InputStream input) {
          int cellCount = INT.from(forceRead(input, ByteUtil.SIZE_OF_INT));
          if (cellCount < 0)
            throw new IllegalStateException("the number from cell should be bigger than zero");
          Cell<?>[] cells =
              IntStream.range(0, cellCount)
                  .mapToObj(
                      i -> {
                        int nameSize = SHORT.from(forceRead(input, ByteUtil.SIZE_OF_SHORT));
                        String name = STRING.from(forceRead(input, nameSize));
                        DataType type =
                            DataType.of(SHORT.from(forceRead(input, ByteUtil.SIZE_OF_SHORT)));
                        final Cell<?> cell;
                        short valueSize = SHORT.from(forceRead(input, ByteUtil.SIZE_OF_SHORT));
                        switch (type) {
                          case BYTES:
                            cell = Cell.of(name, BYTES.from(forceRead(input, valueSize)));
                            break;
                          case BOOLEAN:
                            cell = Cell.of(name, BOOLEAN.from(forceRead(input, valueSize)));
                            break;
                          case BYTE:
                            cell = Cell.of(name, BYTE.from(forceRead(input, valueSize)));
                            break;
                          case SHORT:
                            cell = Cell.of(name, SHORT.from(forceRead(input, valueSize)));
                            break;
                          case INT:
                            cell = Cell.of(name, INT.from(forceRead(input, valueSize)));
                            break;
                          case LONG:
                            cell = Cell.of(name, LONG.from(forceRead(input, valueSize)));
                            break;
                          case FLOAT:
                            cell = Cell.of(name, FLOAT.from(forceRead(input, valueSize)));
                            break;
                          case DOUBLE:
                            cell = Cell.of(name, DOUBLE.from(forceRead(input, valueSize)));
                            break;
                          case STRING:
                            cell = Cell.of(name, STRING.from(forceRead(input, valueSize)));
                            break;
                          case ROW:
                            cell = Cell.of(name, ROW.from(forceRead(input, valueSize)));
                            break;
                          case OBJECT:
                            cell = Cell.of(name, OBJECT.from(forceRead(input, valueSize)));
                            break;
                          default:
                            throw new UnsupportedClassVersionError(type.getClass().getName());
                        }
                        return cell;
                      })
                  .toArray(Cell[]::new);
          int tagCount = SHORT.from(forceRead(input, ByteUtil.SIZE_OF_SHORT));
          if (tagCount < 0)
            throw new IllegalStateException("the number from tag should be bigger than zero");
          List<String> tag =
              IntStream.range(0, tagCount)
                  .mapToObj(
                      i ->
                          STRING.from(
                              forceRead(
                                  input, SHORT.from(forceRead(input, ByteUtil.SIZE_OF_SHORT)))))
                  .collect(Collectors.toList());
          return Row.of(tag, cells);
        }
      };

  Serializer<Object> OBJECT =
      new Serializer<Object>() {
        @Override
        public byte[] to(Object obj) {
          try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
              ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(obj);
            out.flush();
            return bytes.toByteArray();
          } catch (IOException e) {
            throw new IllegalArgumentException(e);
          }
        }

        @Override
        public Object from(byte[] bytes) {
          try (ByteArrayInputStream bs = new ByteArrayInputStream(bytes);
              ObjectInputStream input = new ObjectInputStream(bs)) {
            return input.readObject();
          } catch (IOException | java.lang.ClassNotFoundException e) {
            throw new IllegalArgumentException(e);
          }
        }
      };
}
