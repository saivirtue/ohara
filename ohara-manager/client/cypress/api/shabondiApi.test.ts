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
import * as topicApi from '../../src/api/topicApi';
import * as shabondiApi from '../../src/api/shabondiApi';
import * as inspectApi from '../../src/api/inspectApi';
import {
  createServicesInNodes,
  deleteAllServices,
  assertSettingsByDefinitions,
} from '../utils';
import { SERVICE_STATE } from '../../src/api/apiInterface/clusterInterface';
import { SOURCES } from '../../src/api/apiInterface/connectorInterface';

const generateShabondi = async () => {
  const { node, broker } = await createServicesInNodes({
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

  const shabondiName = generate.serviceName({ prefix: 'shabondi' });
  const shabondi = {
    name: shabondiName,
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
    tags: {
      name: shabondiName,
    },
  };

  return shabondi;
};

describe('Shabondi API', () => {
  beforeEach(async () => {
    await deleteAllServices();
  });

  it('createShabondi', async () => {
    const shabondi = await generateShabondi();
    const result = await shabondiApi.create(shabondi);
    const info = await inspectApi.getShabondiInfo();
    const defs = info.data.classInfos.find(
      (classInfo) => classInfo.className === shabondi.shabondi__class,
    );

    if (defs) {
      assertSettingsByDefinitions(
        result.data,
        defs.settingDefinitions,
        shabondi,
      );
    } else {
      assert.fail('inspect shabondi should have result');
    }
  });

  it('fetchShabondi', async () => {
    const shabondi = await generateShabondi();
    await shabondiApi.create(shabondi);

    const result = await shabondiApi.get(shabondi);
    const info = await inspectApi.getShabondiInfo();
    const defs = info.data.classInfos.find(
      (classInfo) => classInfo.className === shabondi.shabondi__class,
    );

    if (defs) {
      assertSettingsByDefinitions(
        result.data,
        defs.settingDefinitions,
        shabondi,
      );
    } else {
      assert.fail('inspect shabondi should have result');
    }
  });

  it('fetchShabondis', async () => {
    const shabondi1 = await generateShabondi();
    const shabondi2 = await generateShabondi();

    await shabondiApi.create(shabondi1);
    await shabondiApi.create(shabondi2);

    const result = await shabondiApi.getAll();

    const shabondis = result.data.map((zk) => zk.name);
    expect(shabondis.includes(shabondi1.name)).to.be.true;
    expect(shabondis.includes(shabondi2.name)).to.be.true;
  });

  it('deleteShabondi', async () => {
    const shabondi = await generateShabondi();

    // delete a non-running shabondi
    await shabondiApi.create(shabondi);
    await shabondiApi.remove(shabondi);
    const result = await shabondiApi.getAll();

    const shabondis = result.data.map((shabondi) => shabondi.name);
    expect(shabondis.includes(shabondi.name)).to.be.false;

    // delete a running shabondi
    await shabondiApi.create(shabondi);
    await shabondiApi.start(shabondi);
    const runningRes = await shabondiApi.get(shabondi);
    expect(runningRes.data.state).to.eq(SERVICE_STATE.RUNNING);

    await shabondiApi.stop(shabondi);
    await shabondiApi.remove(shabondi);
  });

  it('updateShabondi', async () => {
    const shabondi = await generateShabondi();
    const newParams = {
      shabondi__client__port: 2222,
    };
    const newShabondi = { ...shabondi, ...newParams };

    await shabondiApi.create(shabondi);

    const result = await shabondiApi.update(newShabondi);
    const info = await inspectApi.getShabondiInfo();
    const defs = info.data.classInfos.find(
      (classInfo) => classInfo.className === shabondi.shabondi__class,
    );

    if (defs) {
      assertSettingsByDefinitions(
        result.data,
        defs.settingDefinitions,
        newShabondi,
      );
    } else {
      assert.fail('inspect shabondi should have result');
    }
  });

  it('startShabondi', async () => {
    const shabondi = await generateShabondi();
    await shabondiApi.create(shabondi);
    const undefinedShabondiRes = await shabondiApi.get(shabondi);
    expect(undefinedShabondiRes.data.state).to.be.undefined;

    await shabondiApi.start(shabondi);
    const runningShabondiRes = await shabondiApi.get(shabondi);
    expect(runningShabondiRes.data.state).to.eq(SERVICE_STATE.RUNNING);
  });

  it('stopShabondi', async () => {
    const shabondi = await generateShabondi();
    await shabondiApi.create(shabondi);
    const undefinedShabondiRes = await shabondiApi.get(shabondi);
    expect(undefinedShabondiRes.data.state).to.be.undefined;

    await shabondiApi.start(shabondi);
    const runningShabondiRes = await shabondiApi.get(shabondi);
    expect(runningShabondiRes.data.state).to.eq(SERVICE_STATE.RUNNING);
    expect(runningShabondiRes.data.nodeNames).have.lengthOf(1);

    await shabondiApi.stop(shabondi);
    const stopShabondiRes = await shabondiApi.get(shabondi);
    expect(stopShabondiRes.data.state).to.be.undefined;

    await shabondiApi.remove(shabondi);
    const result = await shabondiApi.getAll();

    const shabondis = result.data.map((shabondi) => shabondi.name);
    expect(shabondis.includes(shabondi.name)).to.be.false;
  });
});
