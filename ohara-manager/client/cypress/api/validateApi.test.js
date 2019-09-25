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

const setup = () => {
  const nodeName = generate.serviceName({ prefix: 'node' });
  const zookeeperClusterName = generate.serviceName({ prefix: 'zk' });
  const brokerClusterName = generate.serviceName({ prefix: 'bk' });
  const workerClusterName = generate.serviceName({ prefix: 'wk' });
  const topicName = generate.serviceName({ prefix: 'topic' });

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
    zookeeperClusterName,
    nodeNames: [nodeName],
  });

  cy.startBroker(brokerClusterName);

  cy.createWorker({
    name: workerClusterName,
    brokerClusterName,
    nodeNames: [nodeName],
  });

  cy.startWorker(workerClusterName);

  cy.createTopic({
    name: topicName,
    brokerClusterName,
    group: topicGroup,
  }).as('createTopic');

  cy.startTopic(topicGroup, topicName);

  return {
    workerClusterName,
    topicName,
  };
};

const assignValue = (acc, def) => {
  const { defaultValue, key, valueType } = def;
  const stringTypes = ['STRING', 'CLASS', 'PASSWORD', 'JDBC_TABLE', 'TAGS'];
  const numberTypes = ['LONG', 'INT', 'PORT'];

  if (defaultValue) {
    acc[key] = defaultValue;
  } else {
    if (stringTypes.includes(valueType)) {
      acc[key] = generate.word();
    } else if (numberTypes.includes(valueType)) {
      acc[key] = generate.port();
    }
  }

  return acc;
};

describe('Validate API', () => {
  before(() => cy.deleteAllServices());

  it('validateConnector', () => {
    const { workerClusterName, topicName } = setup();

    cy.fetchWorker(workerClusterName).then(response => {
      const connector = response.data.result.connectors[0]; // Test the very first connector

      let params = connector.definitions
        .filter(def => !def.internal)
        .reduce(assignValue, {});

      params = {
        ...params,
        'connector.class': connector.className,
        topicKeys: [{ group: workerClusterName, name: topicName }],
        workerClusterName,
        'jio.binding.port': generate.port(),
      };

      cy.validateConnector(params).then(response => {
        const {
          data: { isSuccess, result },
        } = response;

        const { errorCount, settings } = result;

        expect(isSuccess).to.eq(true);

        expect(errorCount).to.eq(0);
        expect(settings).to.be.an('array');
      });
    });
  });
});
