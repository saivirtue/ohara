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

package com.island.ohara.streams;

import com.island.ohara.common.annotations.VisibleForTesting;
import com.island.ohara.common.data.Row;
import com.island.ohara.common.exception.ExceptionHandler;
import com.island.ohara.common.exception.OharaException;
import com.island.ohara.common.util.CommonUtils;
import com.island.ohara.streams.config.StreamDefUtils;
import com.island.ohara.streams.config.StreamDefinitions;
import com.island.ohara.streams.ostream.LaunchImpl;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.time.Duration;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings({"unchecked", "rawtypes"})
public abstract class StreamApp {

  /** This is a special key that will be passed to container for getting definition */
  public static final String DEFINITION_COMMAND = "-definition";

  private static final Logger log = LoggerFactory.getLogger(StreamApp.class);

  // We set timeout to 30 seconds
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

  // Exception handler
  private static ExceptionHandler handler =
      ExceptionHandler.builder()
          .with(IOException.class, OharaException::new)
          .with(MalformedURLException.class, OharaException::new)
          .with(ClassNotFoundException.class, OharaException::new)
          .build();

  /**
   * Running a standalone streamApp. This method is usually called from the main method. It must not
   * be called more than once otherwise exception will be thrown.
   *
   * <p>Usage :
   *
   * <pre>
   *   public static void main(String[] args){
   *     StreamApp.runStreamApp(MyStreamApp.class);
   *   }
   * </pre>
   *
   * @param theClass the streamApp class that is constructed and extends from {@link StreamApp}
   */
  public static void runStreamApp(Class<? extends StreamApp> theClass) {
    if (StreamApp.class.isAssignableFrom(theClass))
      ExceptionHandler.DEFAULT.handle(
          () -> {
            LaunchImpl.launchApplication(theClass, new Properties());
            return null;
          });
    else
      throw new RuntimeException(
          "Error: " + theClass + " is not a subclass of " + StreamApp.class.getName());
  }

  /** Constructor */
  public StreamApp() {}

  /**
   * Use to define settings for streamApp usage. Default will load the required configurations only.
   *
   * <p>Usage:
   *
   * <pre>
   *   public StreamDefinitions config() {
   *       // define your own configs
   *       return StreamDefinitions.create().add(SettingDef.builder().key(key).group(group).build());
   *   }
   * </pre>
   *
   * @return the defined settings
   */
  public StreamDefinitions config() {
    return StreamDefinitions.create();
  }

  /** User defined initialization before running streamApp */
  public void init() {}

  /**
   * Entry function. <b>Usage:</b>
   *
   * <pre>
   *   ostream
   *    .filter()
   *    .map()
   *    ...
   * </pre>
   *
   * @param ostream the entry object to define logic
   * @param streamDefinitions configuration object
   */
  public abstract void start(OStream<Row> ostream, StreamDefinitions streamDefinitions);

  /**
   * find main entry of jar in ohara environment container
   *
   * @param args arguments
   * @throws OharaException exception
   */
  public static void main(String[] args) throws OharaException {
    Properties props = loadArgs(args);
    String jarUrl = props.getProperty(StreamDefUtils.JAR_URL_DEFINITION.key());
    if (CommonUtils.isEmpty(jarUrl))
      throw new RuntimeException("It seems you are not running in Ohara Environment?");
    File jarFile = downloadJarByUrl(jarUrl);
    Map.Entry<String, URLClassLoader> entry = findStreamAppEntry(jarFile);

    if (entry.getKey().isEmpty()) throw new RuntimeException("cannot find any match entry");
    Class clz = handler.handle(() -> Class.forName(entry.getKey(), true, entry.getValue()));
    if (StreamApp.class.isAssignableFrom(clz)) {
      LaunchImpl.launchApplication(clz, props);
    } else
      throw new RuntimeException(
          "Error: " + clz + " is not a subclass of " + StreamApp.class.getName());
  }

  @VisibleForTesting
  static File downloadJarByUrl(String jarUrl) throws OharaException {
    return handler.handle(
        () -> CommonUtils.downloadUrl(new URL(jarUrl), CONNECT_TIMEOUT, READ_TIMEOUT));
  }

  @VisibleForTesting
  static Map.Entry<String, URLClassLoader> findStreamAppEntry(File jarFile) throws OharaException {
    String entryClassName = "";

    String jarHeader = "jar:file:";
    String jarTail = "!/";

    // Find the StreamApp entry class name
    JarFile jar = handler.handle(() -> new JarFile(jarFile));
    Enumeration<JarEntry> e = jar.entries();

    URL[] urls = handler.handle(() -> new URL[] {new URL(jarHeader + jarFile + jarTail)});
    URLClassLoader loader = URLClassLoader.newInstance(urls);

    while (e.hasMoreElements()) {
      JarEntry entry = e.nextElement();
      if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
        String className = entry.getName().replace(".class", "").replaceAll("/", ".");
        Class c = handler.handle(() -> loader.loadClass(className));
        if (StreamApp.class.isAssignableFrom(c)) {
          entryClassName = c.getName();
        }
      }
    }
    return new AbstractMap.SimpleEntry<>(entryClassName, loader);
  }

  /**
   * Put the arguments into properties object with format: (String, String)
   *
   * <p>For Example, "arg1=abc arg2 -arg3" will be transformed to ("arg1", "abc"), ("arg2", "") and
   * ("arg3","")
   *
   * @param args argument list
   * @return properties
   */
  private static Properties loadArgs(String[] args) {
    Properties properties = new Properties();
    for (String arg : args) {
      if (!Character.isLetter(arg.charAt(0)) && arg.charAt(0) != '-') {
        log.warn(String.format("We cannot handle the argument: [%s], so we skip it", arg));
        continue;
      }
      String[] prop = arg.split("=");
      properties.setProperty(prop[0], prop.length == 2 ? prop[1] : "");
    }
    return properties;
  }
}
