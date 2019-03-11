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

package com.island.ohara.kafka.exception;

/** CheckedException util */
public class CheckedExceptionUtil {

  /**
   * This method will wrap CheckedException to RuntimeException with lambda
   *
   * <p>guave says that in most case , we case unchecked exception to RuntimeException
   *
   * @see <a
   *     href="https://github.com/google/guava/wiki/Why-we-deprecated-Throwables.propagate">propaget</a>
   * @param cew a lambda throws Exceptions
   * @param <T> exception type
   * @return anything
   */
  public static <T> T wrap(CheckedExceptionWrapperReturn<T> cew) throws RuntimeException {
    try {
      return cew.excute();
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static void wrap(CheckedExceptionWrapper cew) throws RuntimeException {
    try {
      cew.excute();
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static <T> T wrapRuntime(CheckedExceptionWrapperReturn<T> cew) throws RuntimeException {
    try {
      return cew.excute();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static void wrapRuntime(CheckedExceptionWrapper cew) throws RuntimeException {
    try {
      cew.excute();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static <T> T wrap(CheckedExceptionWrapperReturn<T> cew, ExceptionHandler... handlers)
      throws RuntimeException {
    try {
      return cew.excute();
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw ExceptionHandler.handle(e, handlers);
    }
  }

  public static void wrap(CheckedExceptionWrapper cew, ExceptionHandler... handlers)
      throws RuntimeException {
    try {
      cew.excute();
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw ExceptionHandler.handle(e, handlers);
    }
  }

  public static <T> T wrapRuntime(
      CheckedExceptionWrapperReturn<T> cew, ExceptionHandler... handlers) throws OharaException {
    try {
      return cew.excute();
    } catch (Exception e) {
      throw ExceptionHandler.handle(e, handlers);
    }
  }

  public static void wrapRuntime(CheckedExceptionWrapper cew, ExceptionHandler... handlers)
      throws OharaException {
    try {
      cew.excute();
    } catch (Exception e) {
      throw ExceptionHandler.handle(e, handlers);
    }
  }

  public static void rethrow(CheckedExceptionWrapper cew) {
    try {
      cew.excute();
    } catch (Throwable e) {
      rethrow(e);
    }
  }

  public static <T> T rethrow(CheckedExceptionWrapperReturn<T> cew) {
    try {
      return cew.excute();
    } catch (Throwable e) {
      rethrow(e);
    }
    return null;
  }

  /**
   * It's very tricky. This method will throw a cheked exception without checked !!!!!
   *
   * <p>But It also mean that you can't catch this exception in try-catch . Only can catch it by
   * catching Exception.
   */
  @SuppressWarnings("unchecked")
  private static <T extends Throwable> RuntimeException rethrow(Throwable throwable) throws T {
    throw (T) throwable; // rely on vacuous cast
  }
}
