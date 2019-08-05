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

import * as utils from '../utils';

/* eslint-disable no-unused-expressions */
// eslint is complaining about `expect(thing).to.be.undefined`

const setup = () => {
  const nodeName = `node${utils.makeRandomStr()}`;
  const zookeeperClusterName = `zookeeper${utils.makeRandomStr()}`;
  const brokerClusterName = `broker${utils.makeRandomStr()}`;
  const workerClusterName = `worker${utils.makeRandomStr()}`;

  cy.createNode({
    name: nodeName,
    port: 22,
    user: utils.makeRandomStr(),
    password: utils.makeRandomStr(),
  });

  cy.createZookeeper({
    nodeNames: [nodeName],
    name: zookeeperClusterName,
  });

  cy.startZookeeper(zookeeperClusterName);

  cy.createBroker({
    name: brokerClusterName,
    nodeNames: [nodeName],
    zookeeperClusterName,
  });

  cy.startBroker(brokerClusterName);

  const jarKeys = cy.createJar('ohara-it-source.jar').then(res => {
    const { data } = res;
    return {
      name: data.result.name,
      group: data.result.group,
    };
  });

  cy.testCreateWorker({
    name: workerClusterName,
    nodeNames: [nodeName],
    jarKeys,
    brokerClusterName,
  }).as('testCreateWorker');

  cy.startWorker(workerClusterName);

  return {
    nodeName,
    zookeeperClusterName,
    brokerClusterName,
    workerClusterName,
  };
};

describe('Worker API', () => {
  beforeEach(() => cy.deleteAllServices());

  it('createWorker', () => {
    const { workerClusterName } = setup();

    cy.get('@testCreateWorker').then(response => {
      const {
        data: { isSuccess, result },
      } = response;

      expect(isSuccess).to.eq(true);

      expect(result.name).to.eq(workerClusterName);
      expect(result.clientPort).to.be.a('number');
      expect(result.nodeNames)
        .to.be.a('array')
        .that.have.lengthOf(1);
      expect(result.connectors).to.be.a('array');
      expect(result.jarInfos).to.be.a('array');
      expect(result.configTopicName).to.be.a('string');
      expect(result.offsetTopicName).to.be.a('string');
      expect(result.statusTopicName).to.be.a('string');
    });
  });

  it('fetchWorker', () => {
    const { workerClusterName } = setup();

    cy.fetchWorker(workerClusterName).then(response => {
      const {
        data: { isSuccess, result },
      } = response;

      expect(isSuccess).to.eq(true);

      expect(result.name).to.eq(workerClusterName);
      expect(result.clientPort).to.be.a('number');
      expect(result.nodeNames).to.be.a('array');
      expect(result.connectors).to.be.a('array');
      expect(result.jarInfos).to.be.a('array');
    });
  });

  it('fetchWorkers', () => {
    const { nodeName, brokerClusterName } = setup();

    const paramsOne = {
      name: utils.makeRandomStr(),
      brokerClusterName,
      nodeNames: [nodeName],
    };

    const paramsTwo = {
      name: utils.makeRandomStr(),
      brokerClusterName,
      nodeNames: [nodeName],
    };

    cy.testCreateWorker(paramsOne);
    cy.testCreateWorker(paramsTwo);

    cy.fetchWorkers().then(res => {
      const {
        data: { isSuccess, result },
      } = res;

      expect(isSuccess).to.eq(true);

      const workers = result.filter(
        worker =>
          worker.name === paramsOne.name || worker.name === paramsTwo.name,
      );

      expect(workers.length).to.eq(2);
    });
  });

  it('startWorker', () => {
    const { workerClusterName } = setup();

    cy.startWorker(workerClusterName).then(response => {
      expect(response.data.isSuccess).to.eq(true);
    });

    cy.fetchWorker(workerClusterName).then(response => {
      expect(response.data.result.state).to.eq('RUNNING');
    });
  });

  it('stopWorker', () => {
    const { workerClusterName } = setup();

    cy.startWorker(workerClusterName).then(response => {
      expect(response.data.isSuccess).to.eq(true);
    });

    cy.fetchWorker(workerClusterName).then(response => {
      expect(response.data.result.state).to.eq('RUNNING');
    });

    cy.stopWorker(workerClusterName).then(response => {
      expect(response.data.isSuccess).to.eq(true);
    });

    cy.fetchWorker(workerClusterName).then(response => {
      expect(response.data.result.state).to.eq.undefined;
    });
  });

  it('deleteWorker', () => {
    const { workerClusterName } = setup();

    cy.get('@testCreateWorker').then(response => {
      expect(response.data.isSuccess).to.eq(true);
    });

    cy.stopWorker(workerClusterName);

    // We're not currently using this API in the client, and so it's not
    // listed in the brokerApi.js, so we're asserting the response status
    // not the isSuccess value
    cy.request('DELETE', `api/workers/${workerClusterName}`).then(response => {
      expect(response.status).to.eq(204);
    });

    cy.fetchWorkers().then(response => {
      const targetWorker = response.data.result.find(
        worker => worker.name === workerClusterName,
      );

      expect(targetWorker).to.be.undefined;
    });
  });

  it('forceDeleteWorker', () => {
    const { workerClusterName } = setup();

    cy.get('@testCreateWorker').then(response => {
      expect(response.data.isSuccess).to.eq(true);
    });

    cy.stopWorker(workerClusterName);

    // We're not currently using this API in the client, and so it's not
    // listed in the brokerApi.js, so we're asserting the response status
    // not the isSuccess value
    cy.request('DELETE', `api/workers/${workerClusterName}?force=true`).then(
      response => {
        expect(response.status).to.eq(204);
      },
    );

    cy.fetchWorkers().then(response => {
      const targetWorker = response.data.result.find(
        worker => worker.name === workerClusterName,
      );

      expect(targetWorker).to.be.undefined;
    });
  });
});
