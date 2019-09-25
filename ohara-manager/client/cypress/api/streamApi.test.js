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
  let streamName = generate.serviceName({ prefix: 'stream' });

  const streamGroup = workerClusterName;
  const topicGroup = workerClusterName;

  cy.createNode({
    name: nodeName,
    port: generate.port(),
    user: generate.userName(),
    password: generate.password(),
  });

  // Configurator define the cluster for streamapp in creation phase. Hence, we have to set up the broker cluster
  // before running stream APIs tests.
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

  cy.createWorker({
    name: workerClusterName,
    nodeNames: [nodeName],
    brokerClusterName,
  }).as('createWorker');

  cy.startWorker(workerClusterName);

  cy.createJar('ohara-it-source.jar', workerClusterName).then(response => {
    const params = {
      jarKey: {
        name: response.data.result.name,
        group: streamGroup,
      },
      name: streamName,
      group: streamGroup,
      tags: {
        name: streamName,
      },
    };

    cy.createProperty(params)
      .then(response => response)
      .as('createProperty');
  });

  return {
    nodeName,
    zookeeperClusterName,
    brokerClusterName,
    streamName,
    streamGroup,
    topicGroup,
  };
};

describe('Stream property API', () => {
  beforeEach(() => cy.deleteAllServices());

  it('createProperty', () => {
    const { brokerClusterName } = setup();
    const streamName = generate.serviceName({ prefix: 'stream' });

    cy.createJar('ohara-it-source.jar').then(response => {
      const params = {
        jarKey: {
          name: response.data.result.name,
          group: response.data.result.group,
        },
        brokerClusterName,
        name: streamName,
        tags: {
          name: streamName,
        },
      };

      cy.createProperty(params).then(response => {
        const {
          data: { isSuccess, result },
        } = response;
        const { settings } = result;

        expect(isSuccess).to.eq(true);

        expect(settings).to.be.an('object');
        expect(settings.tags.name).to.eq(settings.name);
      });
    });
  });

  it('fetchProperty', () => {
    const { streamName, streamGroup } = setup();

    cy.fetchProperty(streamGroup, streamName).then(response => {
      const {
        data: { isSuccess, result },
      } = response;
      const { settings } = result;

      expect(isSuccess).to.eq(true);

      expect(settings).to.be.an('object');
      expect(settings.tags.name).to.eq(settings.name);
    });
  });

  it('updateProperty', () => {
    const { streamName, streamGroup } = setup();

    const params = {
      name: streamName,
      group: streamGroup,
      params: {
        instances: 1,
        from: [],
        to: [],
      },
    };

    cy.updateProperty(params).then(response => {
      const {
        data: { isSuccess, result },
      } = response;
      const { settings } = result;

      expect(isSuccess).to.eq(true);

      expect(settings).to.be.a('object');
      expect(settings.tags.name).to.eq(settings.name);
    });
  });

  it('startStreamApp', () => {
    const { streamName, brokerClusterName, streamGroup, topicGroup } = setup();
    const fromTopicName = generate.serviceName({ prefix: 'topic' });
    const toTopicName = generate.serviceName({ prefix: 'topic' });

    cy.createTopic({
      name: fromTopicName,
      brokerClusterName,
      group: topicGroup,
    }).as('createTopic');

    cy.startTopic(topicGroup, fromTopicName);

    cy.createTopic({
      name: toTopicName,
      brokerClusterName,
      group: topicGroup,
    }).as('createTopic');

    cy.startTopic(topicGroup, toTopicName);

    const params = {
      name: streamName,
      group: streamGroup,
      params: {
        from: [{ group: topicGroup, name: fromTopicName }],
        to: [{ group: topicGroup, name: toTopicName }],
        name: streamName,
        instances: 1,
      },
    };

    cy.updateProperty(params).then(response => {
      expect(response.data.isSuccess).to.eq(true);
    });

    cy.fetchProperty(streamGroup, streamName).then(response => {
      expect(response.state).to.be.undefined;
    });

    cy.startStreamApp(streamGroup, streamName).then(response => {
      expect(response.data.isSuccess).to.eq(true);
    });

    cy.fetchProperty(streamGroup, streamName).then(response => {
      expect(response.data.result.state).to.eq('RUNNING');
    });
  });

  it('stopStreamApp', () => {
    const { streamName, brokerClusterName, topicGroup, streamGroup } = setup();
    const fromTopicName = generate.serviceName({ prefix: 'topic' });
    const toTopicName = generate.serviceName({ prefix: 'topic' });

    cy.createTopic({
      name: fromTopicName,
      brokerClusterName,
      group: topicGroup,
    }).as('createTopic');

    cy.startTopic(topicGroup, fromTopicName);

    cy.createTopic({
      name: toTopicName,
      brokerClusterName,
      group: topicGroup,
    }).as('createTopic');

    cy.startTopic(topicGroup, toTopicName);

    const params = {
      name: streamName,
      group: streamGroup,
      params: {
        from: [{ group: topicGroup, name: fromTopicName }],
        to: [{ group: topicGroup, name: toTopicName }],
        name: streamName,
        instances: 1,
      },
    };

    cy.updateProperty(params).then(response => {
      expect(response.data.isSuccess).to.eq(true);
    });

    cy.startStreamApp(streamGroup, streamName).then(response => {
      expect(response.data.isSuccess).to.eq(true);
    });

    cy.fetchProperty(streamGroup, streamName).then(response => {
      expect(response.data.result.state).to.eq('RUNNING');
    });

    cy.stopStreamApp(streamGroup, streamName).then(response => {
      expect(response.data.isSuccess).to.eq(true);
    });

    cy.fetchProperty(streamGroup, streamName).then(response => {
      expect(response.data.result.state).to.eq.undefined;
    });
  });

  it('deleteProperty', () => {
    const { streamName, streamGroup } = setup();

    cy.deleteProperty(streamGroup, streamName).then(response => {
      expect(response.data.isSuccess).to.eq(true);
    });
  });
});
