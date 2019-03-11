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

package com.island.ohara.common.util;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CommonUtil {
  private static final Logger logger = LoggerFactory.getLogger(CommonUtil.class);

  /** An interface used to represent current time. */
  @FunctionalInterface
  public interface Timer {
    /** @return current time in ms. */
    long current();
  }

  /** Wrap to {@link System#currentTimeMillis} */
  private static final Timer DEFAULT_TIMER = System::currentTimeMillis;

  private static volatile Timer TIMER = DEFAULT_TIMER;

  public static void inject(Timer newOne) {
    TIMER = newOne;
  }

  public static void reset() {
    TIMER = DEFAULT_TIMER;
  }

  public static long current() {
    return TIMER.current();
  }

  /**
   * create a uuid. This uuid consists of "number" and [a-zA-Z]
   *
   * @return uuid
   */
  public static String uuid() {
    return java.util.UUID.randomUUID().toString();
  }

  /**
   * a random string based on uuid without "-"
   *
   * @return random string
   */
  public static String randomString() {
    return uuid().replaceAll("-", "");
  }

  /**
   * create a random string with specified length. This uuid consists of "number" and [a-zA-Z]
   *
   * @param len the length of uuid
   * @return uuid
   */
  public static String randomString(int len) {
    String string = randomString();
    if (string.length() < len)
      throw new IllegalArgumentException(
          "expected size:" + len + ", actual size:" + string.length());
    return string.substring(0, len);
  }

  /**
   * Determines the IP address of a host, given the host's name.
   *
   * @param hostname host's name
   * @return the IP address string in textual presentation.
   */
  public static String address(String hostname) {
    try {
      return InetAddress.getByName(hostname).getHostAddress();
    } catch (UnknownHostException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public static String hostname() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public static String anyLocalAddress() {
    return "0.0.0.0";
  }

  public static String timezone() {
    return Calendar.getInstance().getTimeZone().getID();
  }

  /**
   * compose a full path based on parent (folder) and name (file).
   *
   * @param parent parent folder
   * @param name file name
   * @return path
   */
  public static String path(String parent, String name) {
    if (parent.endsWith("/")) return parent + name;
    else return parent + "/" + name;
  }

  /**
   * extract the file name from the path
   *
   * @param path path
   * @return name
   */
  public static String name(String path) {
    if (path.equalsIgnoreCase("/")) throw new IllegalArgumentException("no file name for " + path);
    else {
      int last = path.lastIndexOf("/");
      if (last == -1) return path;
      else return path.substring(last + 1);
    }
  }

  /**
   * replace the path's parent path by new parent
   *
   * @param parent new parent
   * @param path original path
   * @return new path
   */
  public static String replaceParent(String parent, String path) {
    return path(parent, name(path));
  }

  /**
   * helper method. Loop the specified method until timeout or get true from method
   *
   * @param f function
   * @param d duration
   * @return false if timeout and (useException = true). Otherwise, the return value is true
   */
  public static Boolean await(Supplier<Boolean> f, Duration d) {
    return await(f, d, Duration.ofMillis(1500), true);
  }

  /**
   * helper method. Loop the specified method until timeout or get true from method
   *
   * @param f function
   * @param d duration
   * @param freq frequency to call the method
   * @param useException true make this method throw exception after timeout.
   * @return false if timeout and (useException = true). Otherwise, the return value is true
   */
  public static Boolean await(
      Supplier<Boolean> f, Duration d, Duration freq, Boolean useException) {
    long startTs = current();
    while (d.toMillis() >= (current() - startTs)) {
      if (f.get()) return true;
      else {
        try {
          TimeUnit.MILLISECONDS.sleep(freq.toMillis());
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
    }
    if (useException) {
      logger.error(
          "Running test method time is "
              + (current() - startTs)
              + " seconds more than the timeout time "
              + d.getSeconds()
              + " seconds. Please turning your timeout time.");
      throw new IllegalStateException("timeout");
    } else return false;
  }

  public static <E1> boolean equals(Set<E1> s1, Object o) {
    if (s1 == o) return true;
    if (!(o instanceof Set)) return false;
    Set<?> s2 = ((Set<?>) o);
    // check empty
    if (s1.isEmpty() && s2.isEmpty()) return true;

    if (s1.size() != s2.size()) return false;

    try {
      return s1.containsAll(s2);
    } catch (ClassCastException | NullPointerException var4) {
      return false;
    }
  }

  /**
   * @param m1 map1
   * @param m2 map2
   * @param condition value eqauls condition
   */
  private static <K, V> boolean mapEquals(
      Map<K, V> m1, Map<?, ?> m2, BiPredicate<V, Object> condition) {

    try {
      for (Map.Entry<K, V> e : m1.entrySet()) {
        K key = e.getKey();
        V value = e.getValue();
        // value null
        if (value == null) {
          // not have key or value is null
          if (m2.get(key) == null && m2.containsKey(key)) continue;
          return false;
        } else {
          if (!condition.test(value, m2.get(key))) return false;
        }
      }
    } catch (ClassCastException | NullPointerException unused) {
      return false;
    }
    return true;
  }

  public static <K, V> boolean equals(Map<K, V> m1, Object o) {

    if (m1 == o) return true;
    if (!(o instanceof Map)) return false;
    Map<?, ?> m2 = ((Map<?, ?>) o);
    // check empty
    if (m1.isEmpty() && m2.isEmpty()) return true;
    if (m1.size() != m2.size()) {
      return false;
    }
    V valueHead = m1.entrySet().iterator().next().getValue();

    // nested
    if (valueHead instanceof List) {
      return mapEquals(m1, m2, (a, b) -> equals((List<?>) a, b));
    } else if (valueHead instanceof Set) {
      return mapEquals(m1, m2, (a, b) -> equals((Set<?>) a, b));
    } else if (valueHead instanceof Map) {
      return mapEquals(m1, m2, (a, b) -> equals((Map<?, ?>) a, b));
    } else {
      return mapEquals(m1, m2, Objects::equals);
    }
  }

  /**
   * List equals
   *
   * @param l1
   * @param l2
   * @param condition
   * @see java.util.AbstractList
   * @return
   */
  private static <E1> boolean listEquals(
      List<E1> l1, List<?> l2, BiPredicate<E1, Object> condition) {
    Iterator<E1> e1 = l1.listIterator();
    Iterator<?> e2 = l2.listIterator();
    while (e1.hasNext() && e2.hasNext()) {
      E1 o1 = e1.next();
      Object o2 = e2.next();
      if (!condition.test(o1, o2)) return false;
    }
    return (!e1.hasNext()) && (!e2.hasNext());
  }

  public static <E1> boolean equals(List<E1> l1, Object o) {
    if (l1 == o) return true;
    if (!(o instanceof List)) return false;
    List<?> l2 = ((List<?>) o);
    // check empty
    if (l1.isEmpty() && l2.isEmpty()) return true;

    // nested
    E1 head = l1.get(0);
    if (head instanceof List) {
      return listEquals(l1, l2, (a, b) -> equals((List<?>) a, b));
    } else if (head instanceof Set) {
      return listEquals(l1, l2, (a, b) -> equals((Set<?>) a, b));
    } else if (head instanceof Map) {
      return listEquals(l1, l2, (a, b) -> equals((Map<?, ?>) a, b));
    } else {
      return listEquals(l1, l2, Objects::equals);
    }
  }

  /**
   * create a temp file with specified prefix name.
   *
   * @param prefix prefix name
   * @return a temp folder
   */
  public static File createTempFile(String prefix) {
    try {
      Path t = Files.createTempFile(prefix, null);
      return t.toFile();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * create a temp folder with specified prefix name.
   *
   * @param prefix prefix name
   * @return a temp folder
   */
  public static File createTempDir(String prefix) {
    try {
      Path t = Files.createTempDirectory(prefix);
      return t.toFile();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public static int resolvePort(int port) {
    if (port <= 0) return availablePort();
    else return port;
  }

  public static int availablePort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      socket.setReuseAddress(true);
      return socket.getLocalPort();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Delete the file or folder
   *
   * @param path path to file or folder
   */
  public static void deleteFiles(File path) {
    try {
      FileUtils.forceDelete(path);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * @param s string
   * @return true if s is null or empty. otherwise false
   */
  public static boolean isEmpty(String s) {
    return s == null || s.isEmpty();
  }

  public static String requireNonEmpty(String s) {
    return requireNonEmpty(s, () -> "");
  }
  /**
   * throw exception if the input string is either null or empty.
   *
   * @param s input string
   * @param msg error message
   * @throws NullPointerException if {@code s} is {@code null}
   * @throws IllegalArgumentException if {@code s} is empty
   * @return input string
   */
  public static String requireNonEmpty(String s, Supplier<String> msg) {
    if (Objects.requireNonNull(s).isEmpty()) throw new IllegalArgumentException(msg.get());
    return s;
  }

  public static <T extends Collection<?>> T requireNonEmpty(T s) {
    return requireNonEmpty(s, () -> "");
  }

  /**
   * throw exception if the input collection is either null or empty.
   *
   * @param s input collection
   * @param msg error message
   * @param <T> collection type
   * @throws NullPointerException if {@code s} is {@code null}
   * @throws IllegalArgumentException if {@code s} is empty
   * @return input collection
   */
  public static <T extends Collection<?>> T requireNonEmpty(T s, Supplier<String> msg) {
    if (Objects.requireNonNull(s).isEmpty()) throw new IllegalArgumentException(msg.get());
    return s;
  }

  public static int requirePositiveInt(int value) {
    return (int) requirePositiveLong(value);
  }

  public static int requirePositiveInt(int value, Supplier<String> msg) {
    return (int) requirePositiveLong(value, msg);
  }

  public static short requirePositiveShort(short value) {
    return (short) requirePositiveLong(value);
  }

  public static short requirePositiveShort(short value, Supplier<String> msg) {
    return (short) requirePositiveLong(value, msg);
  }

  public static long requirePositiveLong(long value) {
    return requirePositiveLong(value, () -> value + " can't be negative");
  }

  public static long requirePositiveLong(long value, Supplier<String> msg) {
    if (value < 0) throw new IllegalArgumentException(msg.get());
    return value;
  }

  /**
   * We should all love simple string, shouldn't we?
   *
   * @param s string
   * @return true if the string consist of number and char. Otherwise, false
   */
  public static boolean onlyNumberAndChar(String s) {
    return s.matches("^[a-zA-Z0-9]*$");
  }

  /**
   * a helper method to "filter" the legal string.
   *
   * @param s string
   * @return origin string
   */
  public static String assertOnlyNumberAndChar(String s) {
    if (onlyNumberAndChar(s)) return s;
    else throw new IllegalArgumentException("Only number and char are accepted!!! actual:" + s);
  }

  /** disable to instantiate CommonUtil. */
  private CommonUtil() {}
}
