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
// eslint is complaining about `expect(thing).to.be.undefined`

import * as generate from '../../src/utils/generate';
import * as connectorApi from '../../src/api/connectorApi';
import * as topicApi from '../../src/api/topicApi';
import { createServices, deleteAllServices } from '../utils';

const generateConnector = async () => {
  const { node, broker, worker } = await createServices({
    withWorker: true,
    withBroker: true,
    withZookeeper: true,
    withNode: true,
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
    connector__class: connectorApi.connectorSources.perf,
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
    const { metrics, lastModified, tasksStatus } = result;
    const {
      columns,
      connector__class,
      name,
      group,
      key__converter,
      value__converter,
      topicKeys,
      workerClusterKey,
      tags,
    } = result.settings;

    expect(metrics).to.be.an('object');
    expect(metrics.meters).to.be.an('array');

    expect(lastModified).to.be.a('number');

    expect(tasksStatus).to.be.an('array');

    expect(columns).to.be.an('array');

    expect(connector__class).to.be.an('string');
    expect(connector__class).to.eq(connector.connector__class);

    expect(name).to.be.a('string');
    expect(name).to.eq(connector.name);

    expect(group).to.be.a('string');
    expect(group).to.eq(connector.group);

    expect(key__converter).to.be.a('string');

    expect(value__converter).to.be.a('string');

    expect(topicKeys).to.be.an('array');

    expect(workerClusterKey).to.be.an('object');
    expect(workerClusterKey).to.be.deep.eq(connector.workerClusterKey);

    expect(tags.name).to.eq(connector.name);
  });

  it('fetchConnector', async () => {
    const connector = await generateConnector();
    await connectorApi.create(connector);

    const result = await connectorApi.get(connector);
    const { metrics, lastModified, tasksStatus } = result;
    const {
      columns,
      connector__class,
      name,
      group,
      key__converter,
      value__converter,
      topicKeys,
      workerClusterKey,
      tags,
    } = result.settings;

    expect(metrics).to.be.an('object');
    expect(metrics.meters).to.be.an('array');

    expect(lastModified).to.be.a('number');

    expect(tasksStatus).to.be.an('array');

    expect(columns).to.be.an('array');

    expect(connector__class).to.be.an('string');
    expect(connector__class).to.eq(connector.connector__class);

    expect(name).to.be.a('string');
    expect(name).to.eq(connector.name);

    expect(group).to.be.a('string');
    expect(group).to.eq(connector.group);

    expect(key__converter).to.be.a('string');

    expect(value__converter).to.be.a('string');

    expect(topicKeys).to.be.an('array');

    expect(workerClusterKey).to.be.an('object');
    expect(workerClusterKey).to.be.deep.eq(connector.workerClusterKey);

    expect(tags.name).to.eq(connector.name);
  });

  it('fetchConnectors', async () => {
    const connectorOne = await generateConnector();
    const connectorTwo = await generateConnector();

    await connectorApi.create(connectorOne);
    await connectorApi.create(connectorTwo);

    const result = await connectorApi.getAll();
    const connectors = result.map(connector => connector.settings.name);
    expect(connectors.includes(connectorOne.name)).to.be.true;
    expect(connectors.includes(connectorTwo.name)).to.be.true;
  });

  it('deleteConnector', async () => {
    const connector = await generateConnector();

    // delete a non-running connector
    await connectorApi.create(connector);
    const result = await connectorApi.remove(connector);
    const brokers = result.map(connector => connector.settings.name);
    expect(brokers.includes(connector.name)).to.be.false;

    // delete a running connector
    await connectorApi.create(connector);
    const runningRes = await connectorApi.start(connector);
    expect(runningRes.status.state).to.eq('RUNNING');

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

    const { metrics, lastModified, tasksStatus } = result;
    const {
      columns,
      connector__class,
      name,
      group,
      key__converter,
      value__converter,
      topicKeys,
      workerClusterKey,
      perf__batch,
      tasks__max,
      tags,
    } = result.settings;

    expect(metrics).to.be.an('object');
    expect(metrics.meters).to.be.an('array');

    expect(lastModified).to.be.a('number');

    expect(tasksStatus).to.be.an('array');

    expect(columns).to.be.an('array');

    expect(connector__class).to.be.an('string');
    expect(connector__class).to.eq(connector.connector__class);

    expect(name).to.be.a('string');
    expect(name).to.eq(connector.name);

    expect(group).to.be.a('string');
    expect(group).to.eq(connector.group);

    expect(key__converter).to.be.a('string');

    expect(value__converter).to.be.a('string');

    expect(topicKeys).to.be.an('array');

    expect(perf__batch).to.be.a('number');
    expect(perf__batch).to.eq(newParams.perf__batch);

    expect(tasks__max).to.be.a('number');
    expect(tasks__max).to.eq(newParams.tasks__max);

    expect(workerClusterKey).to.be.an('object');
    expect(workerClusterKey).to.be.deep.eq(connector.workerClusterKey);

    expect(tags.name).to.eq(connector.name);
  });

  it('startConnector', async () => {
    const connector = await generateConnector();

    await connectorApi.create(connector);
    const undefinedConnectorRes = await connectorApi.get(connector);
    expect(undefinedConnectorRes.status).to.be.undefined;

    const runningConnectorRes = await connectorApi.start(connector);
    expect(runningConnectorRes.status.state).to.eq('RUNNING');
  });

  it('stopConnector', async () => {
    const connector = await generateConnector();

    await connectorApi.create(connector);
    const undefinedConnectorRes = await connectorApi.get(connector);
    expect(undefinedConnectorRes.status).to.be.undefined;

    const runningConnectorRes = await connectorApi.start(connector);
    expect(runningConnectorRes.status.state).to.eq('RUNNING');

    const stopConnectorRes = await connectorApi.stop(connector);
    expect(stopConnectorRes.status).to.be.undefined;

    const result = await connectorApi.remove(connector);
    const connectors = result.map(connector => connector.settings.name);
    expect(connectors.includes(connector.name)).to.be.false;
  });
});