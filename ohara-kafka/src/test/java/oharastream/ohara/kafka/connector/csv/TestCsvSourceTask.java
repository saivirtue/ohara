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

package oharastream.ohara.kafka.connector.csv;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import oharastream.ohara.common.exception.NoSuchFileException;
import oharastream.ohara.common.rule.OharaTest;
import oharastream.ohara.common.setting.ConnectorKey;
import oharastream.ohara.common.setting.TopicKey;
import oharastream.ohara.common.util.CommonUtils;
import oharastream.ohara.kafka.connector.TaskSetting;
import oharastream.ohara.kafka.connector.csv.source.CsvDataReader;
import oharastream.ohara.kafka.connector.json.ConnectorFormatter;
import oharastream.ohara.kafka.connector.storage.FileSystem;
import oharastream.ohara.kafka.connector.storage.FileType;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestCsvSourceTask extends OharaTest {
  private Map<String, String> settings = new HashMap<String, String>();

  @Before
  public void before() {
    // The setting is fake
    settings.put(CsvConnectorDefinitions.INPUT_FOLDER_KEY, "/input");
    settings.put(CsvConnectorDefinitions.COMPLETED_FOLDER_KEY, "/completed");
    settings.put(CsvConnectorDefinitions.ERROR_FOLDER_KEY, "/error");
    settings.put(CsvConnectorDefinitions.TASK_TOTAL_KEY, "1");
    settings.put(CsvConnectorDefinitions.TASK_HASH_KEY, "10");
    settings.put(CsvConnectorDefinitions.FILE_CACHE_SIZE_KEY, "3");
    settings.put(MockCsvSourceTask.MOCK_HOST_NAME_KEY, "host://");
  }

  @Test
  public void testFileQueue() {
    CsvSourceTask sourceTask = new MockCsvSourceTask();

    sourceTask.run(TaskSetting.of(settings));
    Assert.assertEquals(sourceTask.fileNameCacheSize(), 0);

    sourceTask.pollRecords();
    // First poll the element, so queue size is 3 - 1 equals 2
    Assert.assertEquals(sourceTask.fileNameCacheSize(), 2);

    sourceTask.pollRecords();
    Assert.assertEquals(sourceTask.fileNameCacheSize(), 1);

    sourceTask.pollRecords();
    Assert.assertEquals(sourceTask.fileNameCacheSize(), 0);

    sourceTask.pollRecords();
    Assert.assertEquals(sourceTask.fileNameCacheSize(), 2);

    sourceTask.pollRecords();
    Assert.assertEquals(sourceTask.fileNameCacheSize(), 1);

    sourceTask.pollRecords();
    Assert.assertEquals(sourceTask.fileNameCacheSize(), 0);
  }

  @Test
  public void testGetDataReader() {
    CsvSourceTask task = createTask(settings);
    Assert.assertTrue(task.dataReader() instanceof CsvDataReader);
  }

  @Test
  public void testNoSuchFile() {
    CsvSourceTask sourceTask =
        new MockCsvSourceTask() {
          @Override
          public FileSystem fileSystem(TaskSetting settings) {
            settings.stringValue(MOCK_HOST_NAME_KEY); // For get config test
            return new MockCsvSourceFileSystem() {
              @Override
              public FileType fileType(String path) {
                throw new NoSuchFileException("File doesn't exists");
              }
            };
          }
        };

    // Test continue to process other file
    sourceTask.run(TaskSetting.of(settings));
    sourceTask.pollRecords();
    Assert.assertEquals(sourceTask.fileNameCacheSize(), 2);

    sourceTask.pollRecords();
    Assert.assertEquals(sourceTask.fileNameCacheSize(), 1);

    sourceTask.pollRecords();
    Assert.assertEquals(sourceTask.fileNameCacheSize(), 0);
  }

  @Test(expected = RuntimeException.class)
  public void testOtherRuntimeException() {
    CsvSourceTask sourceTask =
        new MockCsvSourceTask() {
          @Override
          public FileSystem fileSystem(TaskSetting settings) {
            settings.stringValue(MOCK_HOST_NAME_KEY); // For get config test
            return new MockCsvSourceFileSystem() {
              @Override
              public FileType fileType(String path) {
                throw new RuntimeException("runinng exception");
              }
            };
          }
        };
    sourceTask.run(TaskSetting.of(settings));
    sourceTask.pollRecords();
  }

  @Test(expected = NoSuchElementException.class)
  public void testGetDataReader_WithEmptyConfig() {
    Map<String, String> settings = new HashMap<String, String>();
    CsvSourceTask task = createTask(settings);
    task.dataReader();
  }

  private CsvSourceTask createTask(Map<String, String> settings) {
    CsvSourceTask task = new MockCsvSourceTask();
    task.start(
        ConnectorFormatter.of()
            .connectorKey(ConnectorKey.of(CommonUtils.randomString(5), CommonUtils.randomString(5)))
            .topicKey(TopicKey.of(CommonUtils.randomString(5), CommonUtils.randomString(5)))
            .settings(settings)
            .raw());
    return task;
  }
}

class MockCsvSourceFileSystem implements FileSystem {

  @Override
  public boolean exists(String path) {
    return false;
  }

  @Override
  public Iterator<String> listFileNames(String dir) {
    return IntStream.range(1, 100)
        .boxed()
        .map(i -> "file" + i)
        .collect(Collectors.toList())
        .iterator();
  }

  @Override
  public FileType fileType(String path) {
    return FileType.FILE;
  }

  @Override
  public OutputStream create(String path) {
    throw new UnsupportedOperationException("Mock not support this function");
  }

  @Override
  public OutputStream append(String path) {
    throw new UnsupportedOperationException("Mock not support this function");
  }

  @Override
  public InputStream open(String path) {
    throw new UnsupportedOperationException("Mock not support this function");
  }

  @Override
  public void delete(String path) {
    throw new UnsupportedOperationException("Mock not support this function");
  }

  @Override
  public void delete(String path, boolean recursive) {
    throw new UnsupportedOperationException("Mock not support this function");
  }

  @Override
  public boolean moveFile(String sourcePath, String targetPath) {
    throw new UnsupportedOperationException("Mock not support this function");
  }

  @Override
  public void mkdirs(String dir) {
    throw new UnsupportedOperationException("Mock not support this function");
  }

  @Override
  public void close() {
    throw new UnsupportedOperationException("Mock not support this function");
  }
}

class MockCsvSourceTask extends CsvSourceTask {
  public static String MOCK_HOST_NAME_KEY = "mock.hostname";

  @Override
  public FileSystem fileSystem(TaskSetting settings) {
    settings.stringValue(MOCK_HOST_NAME_KEY); // For get config test
    return new MockCsvSourceFileSystem();
  }
}
