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

import * as generate from '../../src/utils/generate';
import * as logApi from '../../src/api/logApi';
import * as topicApi from '../../src/api/topicApi';
import * as streamApi from '../../src/api/streamApi';
import * as shabondiApi from '../../src/api/shabondiApi';
import * as fileApi from '../../src/api/fileApi';
import { createServicesInNodes, deleteAllServices } from '../utils';
import { SOURCES } from '../../src/api/apiInterface/connectorInterface';

const file = {
  fixturePath: 'stream',
  // we use an existing file to simulate upload jar
  name: 'ohara-it-stream.jar',
  group: generate.serviceName({ prefix: 'group' }),
};

const generateCluster = async () => {
  const result = await createServicesInNodes({
    withWorker: true,
    withBroker: true,
    withZookeeper: true,
  });
  return result;
};

describe('Log API', () => {
  beforeEach(async () => {
    await deleteAllServices();
    cy.createJar(file).then((params) => fileApi.create(params));
  });

  it('fetchConfiguratorLog', async () => {
    const result = await logApi.getConfiguratorLog();

    const { clusterKey, logs } = result.data;

    expect(clusterKey).to.be.an('object');

    expect(logs).to.be.an('array');
  });

  it('fetchServiceLog', async () => {
    const { node, zookeeper, broker, worker } = await generateCluster();

    const logZookeeper = await logApi.getZookeeperLog(zookeeper);

    expect(logZookeeper.data.clusterKey.name).to.be.eq(zookeeper.name);
    expect(logZookeeper.data.clusterKey.group).to.be.eq(zookeeper.group);
    expect(logZookeeper.data.logs).to.be.an('array');

    const logBroker = await logApi.getBrokerLog(broker);

    expect(logBroker.data.clusterKey.name).to.be.eq(broker.name);
    expect(logBroker.data.clusterKey.group).to.be.eq(broker.group);
    expect(logBroker.data.logs).to.be.an('array');

    const logWorker = await logApi.getWorkerLog(worker);

    expect(logWorker.data.clusterKey.name).to.be.eq(worker.name);
    expect(logWorker.data.clusterKey.group).to.be.eq(worker.group);
    expect(logWorker.data.logs).to.be.an('array');

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

    const stream = {
      name: generate.serviceName({ prefix: 'stream' }),
      group: generate.serviceName({ prefix: 'group' }),
      nodeNames: [node.hostname],
      brokerClusterKey: {
        name: broker.name,
        group: broker.group,
      },
      jarKey: {
        name: file.name,
        group: file.group,
      },
      from: [{ name: topic.name, group: topic.group }],
      to: [{ name: topic.name, group: topic.group }],
    };
    await streamApi.create(stream);
    await streamApi.start(stream);

    const logStream = await logApi.getStreamLog(stream);

    expect(logStream.data.clusterKey.name).to.be.eq(stream.name);
    expect(logStream.data.clusterKey.group).to.be.eq(stream.group);
    expect(logStream.data.logs).to.be.an('array');

    const shabondi = {
      name: generate.serviceName({ prefix: 'shabondi' }),
      group: generate.serviceName({ prefix: 'group' }),
      shabondi__class: SOURCES.shabondi,
      shabondi__client__port: 1234,
      nodeNames: [node.hostname],
      brokerClusterKey: {
        name: broker.name,
        group: broker.group,
      },
      shabondi__source__toTopics: [{ name: topic.name, group: topic.group }],
      shabondi__sink__fromTopics: [{ name: topic.name, group: topic.group }],
    };
    await shabondiApi.create(shabondi);
    await shabondiApi.start(shabondi);

    const logShabondi = await logApi.getShabondiLog(shabondi);

    expect(logShabondi.data.clusterKey.name).to.be.eq(shabondi.name);
    expect(logShabondi.data.clusterKey.group).to.be.eq(shabondi.group);
    expect(logShabondi.data.logs).to.be.an('array');
  });
});
