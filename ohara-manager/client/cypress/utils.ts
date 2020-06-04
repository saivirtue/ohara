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
/* eslint-disable @typescript-eslint/no-unused-expressions */

import { MODE } from '../src/const';
import * as generate from '../src/utils/generate';
import * as nodeApi from '../src/api/nodeApi';
import * as fileApi from '../src/api/fileApi';
import * as zkApi from '../src/api/zookeeperApi';
import * as bkApi from '../src/api/brokerApi';
import * as wkApi from '../src/api/workerApi';
import * as connectorApi from '../src/api/connectorApi';
import * as topicApi from '../src/api/topicApi';
import * as streamApi from '../src/api/streamApi';
import * as shabondiApi from '../src/api/shabondiApi';
import * as pipelineApi from '../src/api/pipelineApi';
import * as objectApi from '../src/api/objectApi';
import * as inspectApi from '../src/api/inspectApi';
import { wait } from '../src/api/utils/waitUtils';
import { API, RESOURCE } from '../src/api/utils/apiUtils';
import {
  ObjectKey,
  BasicResponse,
} from '../src/api/apiInterface/basicInterface';
import { waitForRunning, waitForStopped } from '../src/api/utils/waitUtils';
import {
  SettingDef,
  Type,
  isNumberType,
  isStringType,
  isArrayType,
  isObjectType,
  Necessary,
} from '../src/api/apiInterface/definitionInterface';

export const waitFor = async <T extends BasicResponse>(
  resource: string,
  objectKey: ObjectKey,
  expect: (res: T) => boolean,
) => {
  return await wait({
    api: new API(resource),
    objectKey,
    checkFn: expect,
    // we don't need to retry too frequently
    sleep: 5000,
  });
};

export const createServicesInNodes = async ({
  withWorker = false,
  withBroker = false,
  withZookeeper = false,
} = {}) => {
  let result: { [k: string]: any } = {};

  const node = {
    hostname: generate.serviceName({ prefix: 'node' }),
    port: generate.port(),
    user: generate.userName(),
    password: generate.password(),
  };
  const nodeRes = await nodeApi.create(node);
  result.node = nodeRes.data;

  if (withZookeeper) {
    const zookeeper = {
      name: generate.serviceName({ prefix: 'zk' }),
      group: generate.serviceName({ prefix: 'group' }),
      nodeNames: [node.hostname],
    };
    await zkApi.create(zookeeper);
    await zkApi.start(zookeeper);
    const zkRes = await waitFor(RESOURCE.ZOOKEEPER, zookeeper, waitForRunning);
    result.zookeeper = zkRes.data;

    if (withBroker) {
      const broker = {
        name: generate.serviceName({ prefix: 'bk' }),
        group: generate.serviceName({ prefix: 'group' }),
        nodeNames: [node.hostname],
        zookeeperClusterKey: {
          name: zookeeper.name,
          group: zookeeper.group,
        },
      };
      await bkApi.create(broker);
      await bkApi.start(broker);
      const bkRes = await waitFor(RESOURCE.BROKER, broker, waitForRunning);
      result.broker = bkRes.data;

      if (withWorker) {
        const worker = {
          name: generate.serviceName({ prefix: 'wk' }),
          group: generate.serviceName({ prefix: 'group' }),
          nodeNames: [node.hostname],
          brokerClusterKey: {
            name: broker.name,
            group: broker.group,
          },
        };
        await wkApi.create(worker);
        await wkApi.start(worker);
        const wkRes = await waitFor(RESOURCE.WORKER, worker, waitForRunning);
        result.worker = wkRes.data;
      }
    }
  }

  return result;
};

export const deleteAllServices = async () => {
  // delete all connectors
  const connectRes = await connectorApi.getAll();
  const connectors = connectRes.data;
  // we don't care the execute order of each individual connect was done or not.
  // Using Promise.all() to make sure all connects were stopped & deleted.
  await Promise.all(
    connectors.map((connector) =>
      connectorApi
        .forceStop({ name: connector.name, group: connector.group })
        .then(() =>
          waitFor(
            RESOURCE.CONNECTOR,
            { name: connector.name, group: connector.group },
            waitForStopped,
          ),
        )
        .then(() => {
          connectorApi.remove({
            name: connector.name,
            group: connector.group,
          });
        }),
    ),
  );

  // delete all workers
  const workerRes = await wkApi.getAll();
  const workers = workerRes.data;
  // we don't care the execute order of each individual worker was done or not.
  // Using Promise.all() to make sure all workers were stopped & deleted.
  await Promise.all(
    workers.map((wk) =>
      wkApi
        .forceStop({ name: wk.name, group: wk.group })
        .then(() =>
          waitFor(
            RESOURCE.WORKER,
            { name: wk.name, group: wk.group },
            waitForStopped,
          ),
        )
        .then(() => {
          wkApi.remove({ name: wk.name, group: wk.group });
        }),
    ),
  );

  // delete all streams
  const streamRes = await streamApi.getAll();
  const streams = streamRes.data;
  // we don't care the execute order of each individual stream was done or not.
  // Using Promise.all() to make sure all streams were stopped & deleted.
  await Promise.all(
    streams.map((stream) =>
      streamApi
        .forceStop({ name: stream.name, group: stream.group })
        .then(() =>
          waitFor(
            RESOURCE.STREAM,
            { name: stream.name, group: stream.group },
            waitForStopped,
          ),
        )
        .then(() => {
          streamApi.remove({ name: stream.name, group: stream.group });
        }),
    ),
  );

  // delete all shabondis
  const shabondiRes = await shabondiApi.getAll();
  const shabondis = shabondiRes.data;
  // we don't care the execute order of each individual shabondi was done or not.
  // Using Promise.all() to make sure all shabondis were stopped & deleted.
  await Promise.all(
    shabondis.map((shabondi) =>
      shabondiApi
        .forceStop({ name: shabondi.name, group: shabondi.group })
        .then(() =>
          waitFor(
            RESOURCE.SHABONDI,
            { name: shabondi.name, group: shabondi.group },
            waitForStopped,
          ),
        )
        .then(() => {
          shabondiApi.remove({ name: shabondi.name, group: shabondi.group });
        }),
    ),
  );

  // delete all topics
  const topicRes = await topicApi.getAll();
  const topics = topicRes.data;
  // we don't care the execute order of each individual topic was done or not.
  // Using Promise.all() to make sure all topics were stopped & deleted.
  await Promise.all(
    topics.map((topic) =>
      topicApi.forceStop({ name: topic.name, group: topic.group }).then(() => {
        topicApi.remove({ name: topic.name, group: topic.group });
      }),
    ),
  );

  // delete all brokers
  const brokerRes = await bkApi.getAll();
  const brokers = brokerRes.data;
  // we don't care the execute order of each individual broker was done or not.
  // Using Promise.all() to make sure all brokers were stopped & deleted.
  await Promise.all(
    brokers.map((bk) =>
      bkApi
        .forceStop({ name: bk.name, group: bk.group })
        .then(() =>
          waitFor(
            RESOURCE.BROKER,
            { name: bk.name, group: bk.group },
            waitForStopped,
          ),
        )
        .then(() => {
          bkApi.remove({ name: bk.name, group: bk.group });
        }),
    ),
  );

  // delete all zookeepers
  const zookeeperRes = await zkApi.getAll();
  const zookeepers = zookeeperRes.data;
  // we don't care the execute order of each individual zookeeper was done or not.
  // Using Promise.all() to make sure all zookeepers were stopped & deleted.
  await Promise.all(
    zookeepers.map((zk) =>
      zkApi
        .forceStop({ name: zk.name, group: zk.group })
        .then(() =>
          waitFor(
            RESOURCE.ZOOKEEPER,
            { name: zk.name, group: zk.group },
            waitForStopped,
          ),
        )
        .then(() => {
          zkApi.remove({ name: zk.name, group: zk.group });
        }),
    ),
  );

  // delete all nodes if not k8s mode
  const inspectRes = await inspectApi.getConfiguratorInfo();
  if (inspectRes.data.mode !== MODE.K8S) {
    const nodeRes = await nodeApi.getAll();
    const nodes = nodeRes.data;
    // we don't care the execute order of each individual node was done or not.
    // Using Promise.all() to make sure all nodes were deleted.
    await Promise.all(nodes.map((node) => nodeApi.remove(node.hostname)));
  }

  // delete all files
  const fileRes = await fileApi.getAll();
  const files = fileRes.data;
  // we don't care the execute order of each individual file was done or not.
  // Using Promise.all() to make sure all files were deleted.
  await Promise.all(files.map((file) => fileApi.remove(file)));

  // delete all pipelines
  const pipelineRes = await pipelineApi.getAll();
  const pipelines = pipelineRes.data;
  // we don't care the execute order of each individual pipeline was done or not.
  // Using Promise.all() to make sure all pipelines were deleted.
  await Promise.all(pipelines.map((file) => pipelineApi.remove(file)));

  // delete all objects
  const objectRes = await objectApi.getAll();
  const objects = objectRes.data;
  // we don't care the execute order of each individual object was done or not.
  // Using Promise.all() to make sure all objects were deleted.
  await Promise.all(
    objects.map((object) => objectApi.remove(object as ObjectKey)),
  );
};

export const assertSettingsByDefinitions = (
  data: { [k: string]: any },
  definitions: SettingDef[],
  expectedResult: { [k: string]: any },
) => {
  // check type
  definitions.forEach((definition) => {
    const value = data[definition.key];
    // don't assert the value if:
    // - it's internal
    // - it's optional and no data
    if (
      definition.internal ||
      (definition.necessary === Necessary.OPTIONAL && !value)
    )
      return;

    // 1. value should not be undefined
    expect(value, `${definition.key} should have value`).to.be.not.undefined;

    // 2. check type by valueType
    if (isNumberType(definition.valueType)) {
      expect(value, `${value} should be a number`).to.be.a('number');
    } else if (isStringType(definition.valueType)) {
      expect(value, `${value} should be a string`).to.be.a('string');
    } else if (isArrayType(definition.valueType)) {
      expect(value, `${value} should be an array`).to.be.an('array');
    } else if (isObjectType(definition.valueType)) {
      expect(value, `${value} should be an object`).to.be.an('object');
    } else if (definition.valueType === Type.BOOLEAN) {
      expect(value, `${value} should be a boolean`).to.be.a('boolean');
    }

    // 3. check value
    const expectedValue =
      definition.key in expectedResult
        ? expectedResult[definition.key]
        : definition.defaultValue
        ? definition.defaultValue
        : undefined;

    // don't assert the value which be randomized by configurator
    if (!expectedValue) return;

    expect(value, `[${definition.key}] value assert`).to.be.deep.eq(
      expectedValue,
    );
    if (isArrayType(definition.valueType)) {
      expect(value, `[${definition.key}] length assert`).to.have.lengthOf(
        expectedValue.length,
      );
    }
  });
};
