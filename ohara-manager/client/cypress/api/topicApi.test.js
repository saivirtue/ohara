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

import * as generate from '../../src/utils/generate';

/* eslint-disable no-unused-expressions */
// eslint is complaining about `expect(thing).to.be.undefined`

const setup = () => {
  const nodeName = generate.serviceName({ prefix: 'node' });
  const zookeeperClusterName = generate.serviceName({ prefix: 'zk' });
  const brokerClusterName = generate.serviceName({ prefix: 'bk' });
  const workerClusterName = generate.serviceName({ prefix: 'wk' });
  const topicName = generate.serviceName({ prefix: 'topic' });

  // The worker is not required in the test, but we need to use `workerClusterName`
  // as the topic group to mimic the behvior of our UI code
  const topicGroup = workerClusterName;

  cy.createNode({
    name: nodeName,
    port: generate.port(),
    user: generate.userName(),
    password: generate.password(),
  });

  cy.createZookeeper({
    name: zookeeperClusterName,
    nodeNames: [nodeName],
  });

  cy.startZookeeper(zookeeperClusterName);

  cy.createBroker({
    name: brokerClusterName,
    nodeNames: [nodeName],
    zookeeperClusterName,
  });

  cy.startBroker(brokerClusterName);

  cy.createTopic({
    name: topicName,
    brokerClusterName,
    group: topicGroup,
    tags: {
      name: topicName,
    },
  }).as('createTopic');

  cy.startTopic(topicGroup, topicName);

  return {
    nodeName,
    zookeeperClusterName,
    brokerClusterName,
    topicName,
    topicGroup,
  };
};

describe('Topic API', () => {
  beforeEach(() => cy.deleteAllServices());

  it('CreateTopic', () => {
    const { topicName, topicGroup } = setup();

    cy.get('@createTopic').then(response => {
      const {
        data: { isSuccess, result },
      } = response;
      const {
        name,
        numberOfPartitions,
        numberOfReplications,
        metrics,
        tags,
        group,
      } = result;

      expect(isSuccess).to.eq(true);

      expect(name).to.eq(topicName);
      expect(numberOfPartitions).to.be.a('number');
      expect(numberOfReplications).to.be.a('number');
      expect(metrics).to.be.a('object');
      expect(metrics.meters).to.be.a('array');
      expect(tags.name).to.eq(topicName);
      expect(group).to.eq(topicGroup);
    });
  });

  it('fetchTopic', () => {
    const { topicName, topicGroup } = setup();

    cy.fetchTopic(topicGroup, topicName).then(response => {
      const {
        data: { isSuccess, result },
      } = response;

      const {
        name,
        numberOfPartitions,
        numberOfReplications,
        metrics,
        tags,
        group,
      } = result;

      expect(isSuccess).to.eq(true);

      expect(name).to.eq(topicName);
      expect(numberOfPartitions).to.be.a('number');
      expect(numberOfReplications).to.be.a('number');
      expect(metrics).to.be.a('object');
      expect(metrics.meters).to.be.a('array');
      expect(tags.name).to.eq(topicName);
      expect(group).to.eq(topicGroup);
    });
  });

  it('fetchTopics', () => {
    const { brokerClusterName, topicGroup } = setup();

    const paramsOne = {
      name: generate.serviceName({ prefix: 'topic' }),
      group: topicGroup,
      brokerClusterName,
    };

    const paramsTwo = {
      name: generate.serviceName({ prefix: 'topic' }),
      group: topicGroup,
      brokerClusterName,
    };

    cy.createTopic(paramsOne);
    cy.createTopic(paramsTwo);

    cy.startTopic(topicGroup, paramsOne.name);
    cy.startTopic(topicGroup, paramsTwo.name);

    cy.fetchTopics().then(response => {
      const {
        data: { isSuccess, result },
      } = response;

      expect(isSuccess).to.eq(true);

      const topics = result.filter(
        topic => topic.name === paramsOne.name || topic.name === paramsTwo.name,
      );

      expect(topics.length).to.eq(2);
    });
  });

  it('startTopic', () => {
    const { topicName, topicGroup } = setup();

    cy.startTopic(topicGroup, topicName).then(response => {
      expect(response.data.isSuccess).to.eq(true);
    });

    cy.fetchTopic(topicGroup, topicName).then(response => {
      expect(response.data.result.state).to.eq('RUNNING');
    });
  });

  it('stopTopic', () => {
    const { topicName, topicGroup } = setup();

    cy.startTopic(topicGroup, topicName).then(response => {
      expect(response.data.isSuccess).to.eq(true);
    });

    cy.fetchTopic(topicGroup, topicName).then(response => {
      expect(response.data.result.state).to.eq('RUNNING');
    });

    cy.stopTopic(topicGroup, topicName).then(response => {
      expect(response.data.isSuccess).to.eq(true);
    });

    cy.fetchTopic(topicGroup, topicName).then(response => {
      expect(response.data.result.state).to.eq.undefined;
    });
  });

  it('deleteTopic', () => {
    const { topicName, topicGroup } = setup();

    cy.fetchTopic(topicGroup, topicName).then(response => {
      expect(response.data.isSuccess).to.eq(true);
    });

    cy.stopTopic(topicGroup, topicName).then(response => {
      expect(response.data.isSuccess).to.eq(true);
    });

    cy.deleteTopic(topicGroup, topicName).then(response => {
      expect(response.data.isSuccess).to.eq(true);
    });

    cy.fetchTopics().then(response => {
      const targetTopic = response.data.result.find(
        topic => topic.name === topicName,
      );

      expect(targetTopic).to.be.undefined;
    });
  });
});
