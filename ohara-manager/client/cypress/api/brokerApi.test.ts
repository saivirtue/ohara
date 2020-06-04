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
import * as bkApi from '../../src/api/brokerApi';
import * as inspectApi from '../../src/api/inspectApi';
import {
  createServicesInNodes,
  deleteAllServices,
  assertSettingsByDefinitions,
} from '../utils';
import { SERVICE_STATE } from '../../src/api/apiInterface/clusterInterface';

const generateBroker = async () => {
  const { node, zookeeper } = await createServicesInNodes({
    withZookeeper: true,
  });

  expect(node).to.be.an('object');
  expect(node.hostname).to.be.a('string');

  expect(zookeeper).to.be.an('object');
  expect(zookeeper.name).to.be.a('string');
  expect(zookeeper.group).to.be.a('string');

  const bkName = generate.serviceName({ prefix: 'bk' });
  const broker = {
    name: bkName,
    group: generate.serviceName({ prefix: 'group' }),
    nodeNames: [node.hostname],
    zookeeperClusterKey: {
      name: zookeeper.name,
      group: zookeeper.group,
    },
    tags: {
      name: bkName,
    },
  };
  return broker;
};

describe('Broker API', () => {
  beforeEach(() => deleteAllServices());

  it('createBroker', async () => {
    const bkCluster = await generateBroker();
    const result = await bkApi.create(bkCluster);
    const info = await inspectApi.getBrokerInfo();
    const defs = info.data.settingDefinitions;

    if (defs) {
      assertSettingsByDefinitions(result.data, defs, bkCluster);
    } else {
      assert.fail('inspect broker should have result');
    }
  });

  it('fetchBroker', async () => {
    const bkCluster = await generateBroker();
    await bkApi.create(bkCluster);

    const result = await bkApi.get(bkCluster);
    const info = await inspectApi.getBrokerInfo();
    const defs = info.data.settingDefinitions;

    if (defs) {
      assertSettingsByDefinitions(result.data, defs, bkCluster);
    } else {
      assert.fail('inspect broker should have result');
    }
  });

  it('fetchBrokers', async () => {
    const bkClusterOne = await generateBroker();
    const bkClusterTwo = await generateBroker();

    await bkApi.create(bkClusterOne);
    await bkApi.create(bkClusterTwo);

    const result = await bkApi.getAll();

    const brokers = result.data.map((bk) => bk.name);
    expect(brokers.includes(bkClusterOne.name)).to.be.true;
    expect(brokers.includes(bkClusterTwo.name)).to.be.true;
  });

  it('deleteBroker', async () => {
    const bkCluster = await generateBroker();

    // delete a non-running broker
    await bkApi.create(bkCluster);
    await bkApi.remove(bkCluster);

    const result = await bkApi.getAll();

    const brokers = result.data.map((bk) => bk.name);
    expect(brokers.includes(bkCluster.name)).to.be.false;

    // delete a running broker
    await bkApi.create(bkCluster);
    await bkApi.start(bkCluster);
    const runningRes = await bkApi.get(bkCluster);

    expect(runningRes.data.state).to.eq(SERVICE_STATE.RUNNING);

    await bkApi.stop(bkCluster);
    await bkApi.remove(bkCluster);
  });

  it('updateBroker', async () => {
    const bkCluster = await generateBroker();
    const newParams = {
      clientPort: 2222,
      jmxPort: 3333,
    };
    const newBk = { ...bkCluster, ...newParams };

    await bkApi.create(bkCluster);

    const result = await bkApi.update(newBk);
    const info = await inspectApi.getBrokerInfo();
    const defs = info.data.settingDefinitions;

    if (defs) {
      assertSettingsByDefinitions(result.data, defs, newBk);
    } else {
      assert.fail('inspect broker should have result');
    }
  });

  it('startBroker', async () => {
    const bkCluster = await generateBroker();

    await bkApi.create(bkCluster);
    const undefinedBkRes = await bkApi.get(bkCluster);
    expect(undefinedBkRes.data.state).to.be.undefined;

    await bkApi.start(bkCluster);
    const runningBkRes = await bkApi.get(bkCluster);
    expect(runningBkRes.data.state).to.eq(SERVICE_STATE.RUNNING);
  });

  it('stopBroker', async () => {
    const bkCluster = await generateBroker();

    await bkApi.create(bkCluster);
    const undefinedBkRes = await bkApi.get(bkCluster);
    expect(undefinedBkRes.data.state).to.be.undefined;

    await bkApi.start(bkCluster);
    const runningBkRes = await bkApi.get(bkCluster);
    expect(runningBkRes.data.state).to.eq(SERVICE_STATE.RUNNING);
    expect(runningBkRes.data.nodeNames).have.lengthOf(1);

    await bkApi.stop(bkCluster);
    const stopBkRes = await bkApi.get(bkCluster);
    expect(stopBkRes.data.state).to.be.undefined;

    await bkApi.remove(bkCluster);
    const result = await bkApi.getAll();

    const brokers = result.data.map((bk) => bk.name);
    expect(brokers.includes(bkCluster.name)).to.be.false;
  });

  it('addNodeToBroker', async () => {
    const bkCluster = await generateBroker();

    await bkApi.create(bkCluster);
    const undefinedBkRes = await bkApi.get(bkCluster);
    expect(undefinedBkRes.data.state).to.be.undefined;

    await bkApi.start(bkCluster);
    const runningBkRes = await bkApi.get(bkCluster);
    expect(runningBkRes.data.state).to.eq(SERVICE_STATE.RUNNING);
    expect(runningBkRes.data.nodeNames).have.lengthOf(1);

    const { node: newNode } = await createServicesInNodes();
    await bkApi.addNode(bkCluster, newNode.hostname);
    const result = await bkApi.get(bkCluster);

    const { aliveNodes, lastModified, state, error } = result.data;
    const {
      name,
      group,
      nodeNames,
      clientPort,
      jmxPort,
      imageName,
      tags,
    } = result.data;

    expect(aliveNodes).to.be.an('array');
    expect(aliveNodes).have.lengthOf(2);

    expect(lastModified).to.be.a('number');

    expect(state).to.eq(SERVICE_STATE.RUNNING);

    expect(error).to.be.undefined;

    expect(name).to.be.a('string');
    expect(name).to.eq(bkCluster.name);

    expect(group).to.be.a('string');
    expect(group).to.eq(bkCluster.group);

    expect(nodeNames).to.be.an('array');
    expect(nodeNames).have.lengthOf(2);
    expect(nodeNames.sort()).to.deep.eq(
      bkCluster.nodeNames.concat(newNode.hostname).sort(),
    );

    expect(clientPort).to.be.a('number');
    expect(jmxPort).to.be.a('number');

    expect(imageName).to.be.a('string');

    expect(tags).to.be.an('object');
    expect(tags.name).to.eq(bkCluster.name);
  });

  it('removeNodeFromBroker', async () => {
    const bkCluster = await generateBroker();

    await bkApi.create(bkCluster);
    const undefinedBkRes = await bkApi.get(bkCluster);
    expect(undefinedBkRes.data.state).to.be.undefined;

    await bkApi.start(bkCluster);
    const runningBkRes = await bkApi.get(bkCluster);
    expect(runningBkRes.data.state).to.eq(SERVICE_STATE.RUNNING);
    expect(runningBkRes.data.nodeNames).have.lengthOf(1);

    const { node: newNode } = await createServicesInNodes();
    await bkApi.addNode(bkCluster, newNode.hostname);
    const twoNodeBkData = await bkApi.get(bkCluster);
    expect(twoNodeBkData.data.aliveNodes).to.be.an('array');
    expect(twoNodeBkData.data.aliveNodes).have.lengthOf(2);
    expect(twoNodeBkData.data.nodeNames).to.be.an('array');
    expect(twoNodeBkData.data.nodeNames).have.lengthOf(2);

    await bkApi.removeNode(bkCluster, newNode.hostname);
    const result = await bkApi.get(bkCluster);

    const { lastModified, state, error } = result.data;
    const {
      name,
      group,
      nodeNames,
      clientPort,
      jmxPort,
      imageName,
      tags,
    } = result.data;

    expect(lastModified).to.be.a('number');

    expect(state).to.eq(SERVICE_STATE.RUNNING);

    expect(error).to.be.undefined;

    expect(name).to.be.a('string');
    expect(name).to.eq(bkCluster.name);

    expect(group).to.be.a('string');
    expect(group).to.eq(bkCluster.group);

    expect(nodeNames).to.be.an('array');
    expect(nodeNames).have.lengthOf(1);
    expect(nodeNames).to.deep.eq(bkCluster.nodeNames);

    expect(clientPort).to.be.a('number');
    expect(jmxPort).to.be.a('number');

    expect(imageName).to.be.a('string');

    expect(tags).to.be.an('object');
    expect(tags.name).to.eq(bkCluster.name);
  });
});
