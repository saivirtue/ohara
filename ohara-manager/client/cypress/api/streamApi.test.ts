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
import * as topicApi from '../../src/api/topicApi';
import * as streamApi from '../../src/api/streamApi';
import * as inspectApi from '../../src/api/inspectApi';
import * as fileApi from '../../src/api/fileApi';
import { SERVICE_STATE } from '../../src/api/apiInterface/clusterInterface';
import {
  createServicesInNodes,
  deleteAllServices,
  assertSettingsByDefinitions,
} from '../utils';

const file = {
  fixturePath: 'stream',
  // we use an existing file to simulate upload jar
  name: 'ohara-it-stream.jar',
  group: generate.serviceName({ prefix: 'group' }),
};

const generateStream = async () => {
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

  const streamName = generate.serviceName({ prefix: 'stream' });
  const stream = {
    name: streamName,
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
    tags: {
      name: streamName,
    },
  };

  return stream;
};

describe('Stream API', () => {
  beforeEach(async () => {
    await deleteAllServices();
    cy.createJar(file).then((params) => fileApi.create(params));
  });

  it('createStream', async () => {
    const stream = await generateStream();
    const result = await streamApi.create(stream);
    const info = await inspectApi.getStreamsInfo(stream);
    const defs = info.data.classInfos[0].settingDefinitions;

    if (defs) {
      assertSettingsByDefinitions(result.data, defs, stream);
    } else {
      assert.fail('inspect stream should have result');
    }
  });

  it('fetchStream', async () => {
    const stream = await generateStream();
    await streamApi.create(stream);

    const result = await streamApi.get(stream);
    const info = await inspectApi.getStreamsInfo(stream);
    const defs = info.data.classInfos[0].settingDefinitions;

    if (defs) {
      assertSettingsByDefinitions(result.data, defs, stream);
    } else {
      assert.fail('inspect stream should have result');
    }
  });

  it('fetchStreams', async () => {
    const stream1 = await generateStream();
    const stream2 = await generateStream();

    await streamApi.create(stream1);
    await streamApi.create(stream2);

    const result = await streamApi.getAll();

    const streams = result.data.map((zk) => zk.name);
    expect(streams.includes(stream1.name)).to.be.true;
    expect(streams.includes(stream2.name)).to.be.true;
  });

  it('deleteStream', async () => {
    const stream = await generateStream();

    // delete a non-running stream
    await streamApi.create(stream);
    await streamApi.remove(stream);
    const result = await streamApi.getAll();

    const streams = result.data.map((stream) => stream.name);
    expect(streams.includes(stream.name)).to.be.false;

    // delete a running stream
    await streamApi.create(stream);
    await streamApi.start(stream);
    const runningRes = await streamApi.get(stream);
    expect(runningRes.data.state).to.eq(SERVICE_STATE.RUNNING);

    await streamApi.stop(stream);
    await streamApi.remove(stream);
  });

  it('updateStream', async () => {
    const stream = await generateStream();
    const newParams = {
      jmxPort: 2222,
    };
    const newStream = { ...stream, ...newParams };

    await streamApi.create(stream);

    const result = await streamApi.update(newStream);
    const info = await inspectApi.getStreamsInfo(stream);
    const defs = info.data.classInfos[0].settingDefinitions;

    if (defs) {
      assertSettingsByDefinitions(result.data, defs, newStream);
    } else {
      assert.fail('inspect stream should have result');
    }
  });

  it('startStream', async () => {
    const stream = await generateStream();
    await streamApi.create(stream);
    const undefinedStreamRes = await streamApi.get(stream);
    expect(undefinedStreamRes.data.state).to.be.undefined;

    await streamApi.start(stream);
    const runningStreamRes = await streamApi.get(stream);
    expect(runningStreamRes.data.state).to.eq(SERVICE_STATE.RUNNING);
  });

  it('stopStream', async () => {
    const stream = await generateStream();
    await streamApi.create(stream);
    const undefinedStreamRes = await streamApi.get(stream);
    expect(undefinedStreamRes.data.state).to.be.undefined;

    await streamApi.start(stream);
    const runningStreamRes = await streamApi.get(stream);
    expect(runningStreamRes.data.state).to.eq(SERVICE_STATE.RUNNING);
    expect(runningStreamRes.data.nodeNames).have.lengthOf(1);

    await streamApi.stop(stream);
    const stopStreamRes = await streamApi.get(stream);
    expect(stopStreamRes.data.state).to.be.undefined;

    await streamApi.remove(stream);
    const result = await streamApi.getAll();

    const streams = result.data.map((stream) => stream.name);
    expect(streams.includes(stream.name)).to.be.false;
  });
});
