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

import java.util.*;
import java.util.function.Function;

/** support one-one mapping exceptions */
public class ExceptionHandler {

  private static final Function<Exception, OharaException> DEFAULT_EXCEPTION = OharaException::new;

  private final List<ExceptionPair> excepList;

  public static OharaException handle(Exception e, ExceptionHandler... handlers) {
    Function<Exception, OharaException> f = DEFAULT_EXCEPTION;
    for (int i = handlers.length - 1; i >= 0; i--) {
      if (DEFAULT_EXCEPTION == f) f = handlers[i].getFunction(e);
    }
    return f.apply(e);
  }

  private ExceptionHandler(List<ExceptionPair> excepList) {
    this.excepList = excepList;
  }

  public OharaException handle(Exception e) {
    return getFunction(e).apply(e);
  }

  public OharaException handle(Exception e, ExceptionHandler handler) {
    Function<Exception, OharaException> f = handler.getFunction(e);
    if (DEFAULT_EXCEPTION == f) f = getFunction(e);
    return f.apply(e);
  }

  private Function<Exception, OharaException> getFunction(Exception e) {
    for (ExceptionPair ep : excepList) {
      if (ep.c.isInstance(e)) return ep.f;
    }
    return DEFAULT_EXCEPTION;
  }

  private static class ExceptionPair {
    private final Class<? extends Exception> c;
    private final Function<Exception, OharaException> f;

    private ExceptionPair(Class<? extends Exception> c, Function<Exception, OharaException> f) {
      this.c = c;
      this.f = f;
    }
  }

  public static ExceptionHandlerCreator creator() {
    return new ExceptionHandlerCreator();
  }

  public static class ExceptionHandlerCreator {

    private final List<ExceptionPair> excepList = new ArrayList<>();

    public <T extends Exception> ExceptionHandlerCreator add(
        Class<T> c, Function<T, ? extends OharaException> function) {
      excepList.add(new ExceptionPair(c, e -> function.apply(c.cast(e))));
      return this;
    }

    public ExceptionHandler create() {
      return new ExceptionHandler(excepList);
    }
  }
}
