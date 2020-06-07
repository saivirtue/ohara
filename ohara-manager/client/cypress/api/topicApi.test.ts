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
import * as inspectApi from '../../src/api/inspectApi';
import {
  createServicesInNodes,
  deleteAllServices,
  assertSettingsByDefinitions,
} from '../utils';

const generateTopic = async () => {
  const { node, broker } = await createServicesInNodes({
    withBroker: true,
    withZookeeper: true,
  });
  const topicName = generate.serviceName({ prefix: 'topic' });
  const topic = {
    name: topicName,
    group: generate.serviceName({ prefix: 'group' }),
    nodeNames: [node.hostname],
    brokerClusterKey: {
      name: broker.name,
      group: broker.group,
    },
    tags: {
      name: topicName,
    },
  };
  return topic;
};

describe('Topic API', () => {
  beforeEach(() => deleteAllServices());

  it('createTopic', async () => {
    const topic = await generateTopic();
    const result = await topicApi.create(topic);
    const info = await inspectApi.getBrokerInfo(topic.brokerClusterKey);
    const defs = info.data.classInfos[0].settingDefinitions;

    if (defs) {
      assertSettingsByDefinitions(result.data, defs, topic);
    } else {
      assert.fail('inspect topic should have result');
    }
  });

  it('fetchTopic', async () => {
    const topic = await generateTopic();
    await topicApi.create(topic);

    const result = await topicApi.get(topic);
    const info = await inspectApi.getBrokerInfo(topic.brokerClusterKey);
    const defs = info.data.classInfos[0].settingDefinitions;

    if (defs) {
      assertSettingsByDefinitions(result.data, defs, topic);
    } else {
      assert.fail('inspect topic should have result');
    }
  });

  it('fetchTopics', async () => {
    const topic1 = await generateTopic();
    const topic2 = await generateTopic();
    await topicApi.create(topic1);
    await topicApi.create(topic2);

    const result = await topicApi.getAll();

    const topics = result.data.map((topic) => topic.name);
    expect(topics.includes(topic1.name)).to.be.true;
    expect(topics.includes(topic2.name)).to.be.true;
  });

  it('deleteTopic', async () => {
    const topic = await generateTopic();

    // delete a non-running topic
    await topicApi.create(topic);
    await topicApi.remove(topic);
    const result = await topicApi.getAll();

    const topics = result.data.map((topic) => topic.name);
    expect(topics.includes(topic.name)).to.be.false;

    // delete a running topic
    await topicApi.create(topic);
    await topicApi.start(topic);
    const runningRes = await topicApi.get(topic);
    expect(runningRes.data.state).to.eq('RUNNING');

    await topicApi.stop(topic);
    await topicApi.remove(topic);
  });

  it('updateTopic', async () => {
    const topic = await generateTopic();
    const newParams = {
      numberOfReplications: 5,
      numberOfPartitions: 3,
    };
    const newTopic = { ...topic, ...newParams };

    await topicApi.create(topic);
    const result = await topicApi.update(newTopic);
    const info = await inspectApi.getBrokerInfo(topic.brokerClusterKey);
    const defs = info.data.classInfos[0].settingDefinitions;

    if (defs) {
      assertSettingsByDefinitions(result.data, defs, newTopic);
    } else {
      assert.fail('inspect topic should have result');
    }
  });

  it('startTopic', async () => {
    const topic = await generateTopic();

    await topicApi.create(topic);
    const undefinedTopicRes = await topicApi.get(topic);
    expect(undefinedTopicRes.data.state).to.be.undefined;

    await topicApi.start(topic);
    const runningTopicRes = await topicApi.get(topic);
    expect(runningTopicRes.data.state).to.eq('RUNNING');
  });

  it('stopTopic', async () => {
    const topic = await generateTopic();

    await topicApi.create(topic);
    const undefinedTopicRes = await topicApi.get(topic);
    expect(undefinedTopicRes.data.state).to.be.undefined;

    await topicApi.start(topic);
    const runningTopicRes = await topicApi.get(topic);
    expect(runningTopicRes.data.state).to.eq('RUNNING');

    await topicApi.stop(topic);
    const stopTopicRes = await topicApi.get(topic);
    expect(stopTopicRes.data.state).to.be.undefined;

    await topicApi.remove(topic);
    const result = await topicApi.getAll();

    const topics = result.data.map((topic) => topic.name);
    expect(topics.includes(topic.name)).to.be.false;
  });
});
