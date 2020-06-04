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
import * as zkApi from '../../src/api/zookeeperApi';
import * as inspectApi from '../../src/api/inspectApi';
import {
  createServicesInNodes,
  deleteAllServices,
  assertSettingsByDefinitions,
} from '../utils';
import { SERVICE_STATE } from '../../src/api/apiInterface/clusterInterface';

const generateZookeeper = async () => {
  const { node } = await createServicesInNodes();
  const name = generate.serviceName({ prefix: 'zk' });
  const group = generate.serviceName({ prefix: 'group' });
  const zookeeper = {
    name,
    group,
    nodeNames: [node.hostname],
    tags: {
      name,
    },
  };
  return zookeeper;
};

describe('Zookeeper API', () => {
  beforeEach(() => deleteAllServices());

  it('createZookeeper', async () => {
    const zkCluster = await generateZookeeper();
    const result = await zkApi.create(zkCluster);
    const info = await inspectApi.getZookeeperInfo();
    const defs = info.data.settingDefinitions;

    if (defs) {
      assertSettingsByDefinitions(result.data, defs, zkCluster);
    } else {
      assert.fail('inspect zookeeper should have result');
    }
  });

  it('fetchZookeeper', async () => {
    const zkCluster = await generateZookeeper();
    await zkApi.create(zkCluster);

    const result = await zkApi.get(zkCluster);
    const info = await inspectApi.getZookeeperInfo();
    const defs = info.data.settingDefinitions;

    if (defs) {
      assertSettingsByDefinitions(result.data, defs, zkCluster);
    } else {
      assert.fail('inspect zookeeper should have result');
    }
  });

  it('fetchZookeepers', async () => {
    const zkClusterOne = await generateZookeeper();
    const zkClusterTwo = await generateZookeeper();

    await zkApi.create(zkClusterOne);
    await zkApi.create(zkClusterTwo);

    const result = await zkApi.getAll();

    const zookeepers = result.data.map((zk) => zk.name);
    expect(zookeepers.includes(zkClusterOne.name)).to.be.true;
    expect(zookeepers.includes(zkClusterTwo.name)).to.be.true;
  });

  it('deleteZookeeper', async () => {
    const zkCluster = await generateZookeeper();

    // delete a non-running zookeeper
    await zkApi.create(zkCluster);
    await zkApi.remove(zkCluster);
    const result = await zkApi.getAll();

    const zookeepers = result.data.map((zk) => zk.name);
    expect(zookeepers.includes(zkCluster.name)).to.be.false;

    // delete a running zookeeper
    await zkApi.create(zkCluster);
    await zkApi.start(zkCluster);
    const runningRes = await zkApi.get(zkCluster);

    expect(runningRes.data.state).to.eq(SERVICE_STATE.RUNNING);

    await zkApi.stop(zkCluster);
    await zkApi.remove(zkCluster);
  });

  it('updateZookeeper', async () => {
    const zkCluster = await generateZookeeper();
    const newParams = {
      clientPort: 2222,
      electionPort: 3333,
      peerPort: 4444,
    };
    const newZk = { ...zkCluster, ...newParams };

    await zkApi.create(zkCluster);

    const result = await zkApi.update(newZk);
    const info = await inspectApi.getZookeeperInfo();
    const defs = info.data.settingDefinitions;

    if (defs) {
      assertSettingsByDefinitions(result.data, defs, newZk);
    } else {
      assert.fail('inspect zookeeper should have result');
    }
  });

  it('startZookeeper', async () => {
    const zkCluster = await generateZookeeper();

    await zkApi.create(zkCluster);
    const undefinedZkRes = await zkApi.get(zkCluster);
    expect(undefinedZkRes.data.state).to.be.undefined;

    await zkApi.start(zkCluster);
    const runningZkRes = await zkApi.get(zkCluster);
    expect(runningZkRes.data.state).to.eq(SERVICE_STATE.RUNNING);
  });

  it('stopZookeeper', async () => {
    const zkCluster = await generateZookeeper();

    await zkApi.create(zkCluster);
    const undefinedZkRes = await zkApi.get(zkCluster);
    expect(undefinedZkRes.data.state).to.be.undefined;

    await zkApi.start(zkCluster);
    const runningZkRes = await zkApi.get(zkCluster);
    expect(runningZkRes.data.state).to.eq(SERVICE_STATE.RUNNING);
    expect(runningZkRes.data.nodeNames).have.lengthOf(1);

    await zkApi.stop(zkCluster);
    const stopZkRes = await zkApi.get(zkCluster);
    expect(stopZkRes.data.state).to.be.undefined;

    await zkApi.remove(zkCluster);
    const result = await zkApi.getAll();

    const zookeepers = result.data.map((zk) => zk.name);
    expect(zookeepers.includes(zkCluster.name)).to.be.false;
  });
});
