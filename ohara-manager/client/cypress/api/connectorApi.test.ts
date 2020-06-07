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

/* eslint-disable no-unused-expressions */
/* eslint-disable @typescript-eslint/no-unused-expressions */
// eslint is complaining about `expect(thing).to.be.undefined`

// Note: Do not change the usage of absolute path
// unless you have a solution to resolve TypeScript + Coverage
import * as generate from '../../src/utils/generate';
import * as connectorApi from '../../src/api/connectorApi';
import * as inspectApi from '../../src/api/inspectApi';
import { SOURCES, State } from '../../src/api/apiInterface/connectorInterface';
import * as topicApi from '../../src/api/topicApi';
import {
  createServicesInNodes,
  deleteAllServices,
  assertSettingsByDefinitions,
} from '../utils';

const generateConnector = async () => {
  const { node, broker, worker } = await createServicesInNodes({
    withWorker: true,
    withBroker: true,
    withZookeeper: true,
  });
  const topic = {
    name: generate.serviceName({ prefix: 'topic' }),
    group: generate.serviceName({ prefix: 'group' }),
    nodeNames: [node.hostname],
    brokerClusterKey: {
      name: broker.name,
      group: broker.group,
    },
  };
  await topicApi.create(topic);
  await topicApi.start(topic);

  const connectorName = generate.serviceName({ prefix: 'connector' });
  const connector = {
    name: connectorName,
    group: generate.serviceName({ prefix: 'group' }),
    connector__class: SOURCES.perf,
    perf__frequency: 'PT2S',
    topicKeys: [{ name: topic.name, group: topic.group }],
    workerClusterKey: {
      name: worker.name,
      group: worker.group,
    },
    tags: {
      name: connectorName,
    },
  };
  return connector;
};

describe('Connector API', () => {
  beforeEach(() => deleteAllServices());

  it('createConnector', async () => {
    const connector = await generateConnector();
    const result = await connectorApi.create(connector);
    const info = await inspectApi.getWorkerInfo(connector.workerClusterKey);
    const defs = info.data.classInfos.find(
      (classInfo) => classInfo.className === connector.connector__class,
    );

    if (defs) {
      assertSettingsByDefinitions(
        result.data,
        defs.settingDefinitions,
        connector,
      );
    } else {
      assert.fail('inspect connector should have result');
    }
  });

  it('fetchConnector', async () => {
    const connector = await generateConnector();
    await connectorApi.create(connector);

    const result = await connectorApi.get(connector);
    const info = await inspectApi.getWorkerInfo(connector.workerClusterKey);
    const defs = info.data.classInfos.find(
      (classInfo) => classInfo.className === connector.connector__class,
    );

    if (defs) {
      assertSettingsByDefinitions(
        result.data,
        defs.settingDefinitions,
        connector,
      );
    } else {
      assert.fail('inspect connector should have result');
    }
  });

  it('fetchConnectors', async () => {
    const connectorOne = await generateConnector();
    const connectorTwo = await generateConnector();

    await connectorApi.create(connectorOne);
    await connectorApi.create(connectorTwo);

    const result = await connectorApi.getAll();

    const connectors = result.data.map((connector) => connector.name);
    expect(connectors.includes(connectorOne.name)).to.be.true;
    expect(connectors.includes(connectorTwo.name)).to.be.true;
  });

  it('deleteConnector', async () => {
    const connector = await generateConnector();

    // delete a non-running connector
    await connectorApi.create(connector);
    await connectorApi.remove(connector);
    const result = await connectorApi.getAll();

    const brokers = result.data.map((connector) => connector.name);
    expect(brokers.includes(connector.name)).to.be.false;

    // delete a running connector
    await connectorApi.create(connector);
    await connectorApi.start(connector);
    const runningRes = await connectorApi.get(connector);
    expect(runningRes.data.state).to.eq('RUNNING');

    await connectorApi.stop(connector);
    await connectorApi.remove(connector);
  });

  it('updateConnector', async () => {
    const connector = await generateConnector();
    const newParams = {
      perf__batch: 100,
      tasks__max: 1000,
    };
    const newConnector = { ...connector, ...newParams };

    await connectorApi.create(connector);

    const result = await connectorApi.update(newConnector);
    const info = await inspectApi.getWorkerInfo(connector.workerClusterKey);
    const defs = info.data.classInfos.find(
      (classInfo) => classInfo.className === connector.connector__class,
    );

    if (defs) {
      assertSettingsByDefinitions(
        result.data,
        defs.settingDefinitions,
        newConnector,
      );
    } else {
      assert.fail('inspect connector should have result');
    }
  });

  it('startConnector', async () => {
    const connector = await generateConnector();

    await connectorApi.create(connector);
    const undefinedConnectorRes = await connectorApi.get(connector);
    expect(undefinedConnectorRes.data.state).to.be.undefined;

    await connectorApi.start(connector);
    const runningConnectorRes = await connectorApi.get(connector);

    const {
      state,
      aliveNodes,
      error,
      tasksStatus,
      nodeMetrics,
      lastModified,
    } = runningConnectorRes.data;

    // runtime information should exist
    expect(state).to.eq(State.RUNNING);
    expect(aliveNodes).to.not.be.empty;
    expect(error).to.be.undefined;

    expect(nodeMetrics).to.be.an('object');

    expect(tasksStatus).to.be.not.empty;
    expect(tasksStatus[0].error).to.be.undefined;
    expect(tasksStatus[0].nodeName).to.be.not.empty;
    expect(tasksStatus[0].state).to.eq(State.RUNNING);

    expect(lastModified).to.be.a('number');
  });

  it('stopConnector', async () => {
    const connector = await generateConnector();

    await connectorApi.create(connector);
    const undefinedConnectorRes = await connectorApi.get(connector);
    expect(undefinedConnectorRes.data.state).to.be.undefined;

    await connectorApi.start(connector);
    const runningConnectorRes = await connectorApi.get(connector);

    const {
      state,
      aliveNodes,
      error,
      tasksStatus,
      nodeMetrics,
    } = runningConnectorRes.data;

    // runtime information should exist
    expect(state).to.eq(State.RUNNING);
    expect(aliveNodes).to.not.be.empty;
    expect(error).to.be.undefined;

    expect(nodeMetrics).to.be.an('object');

    expect(tasksStatus).to.be.not.empty;
    expect(tasksStatus[0].error).to.be.undefined;
    expect(tasksStatus[0].nodeName).to.be.not.empty;
    expect(tasksStatus[0].state).to.eq(State.RUNNING);

    await connectorApi.stop(connector);
    const stopConnectorRes = await connectorApi.get(connector);
    expect(stopConnectorRes.data.state).to.be.undefined;

    await connectorApi.remove(connector);
    const result = await connectorApi.getAll();

    const connectors = result.data.map((connector) => connector.name);
    expect(connectors.includes(connector.name)).to.be.false;
  });
});
