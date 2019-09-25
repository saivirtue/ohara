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

import { isEmpty, isUndefined } from 'lodash';

import * as workerApi from '../../src/api/workerApi';
import * as topicApi from '../../src/api/topicApi';
import * as nodeApi from '../../src/api/nodeApi';
import * as pipelineApi from '../../src/api/pipelineApi';
import * as connectorApi from '../../src/api/connectorApi';
import * as streamApp from '../../src/api/streamApi';
import * as zookeeperApi from '../../src/api/zookeeperApi';
import * as brokerApi from '../../src/api/brokerApi';
import { fetchInfo } from '../../src/api/infoApi';
import { axiosInstance } from '../../src/api/apiUtils';
import { fetchJars } from '../../src/api/jarApi';
import { fetchLogs } from '../../src/api/logApi';
import { validateConnector } from '../../src/api/validateApi';

// Node API
Cypress.Commands.add('fetchNodes', () => nodeApi.fetchNodes());
Cypress.Commands.add('updateNode', params => nodeApi.updateNode(params));
Cypress.Commands.add('createNode', params => nodeApi.createNode(params));

// Zookeeper API
Cypress.Commands.add('fetchZookeeper', name =>
  zookeeperApi.fetchZookeeper(name),
);
Cypress.Commands.add('fetchZookeepers', () => zookeeperApi.fetchZookeepers());
Cypress.Commands.add('deleteZookeeper', name =>
  zookeeperApi.deleteZookeeper(name),
);
Cypress.Commands.add('stopZookeeper', name => zookeeperApi.stopZookeeper(name));
Cypress.Commands.add('startZookeeper', name =>
  zookeeperApi.startZookeeper(name),
);
Cypress.Commands.add('createZookeeper', params =>
  zookeeperApi.createZookeeper(params),
);

// Broker API
Cypress.Commands.add('createBroker', params => brokerApi.createBroker(params));
Cypress.Commands.add('fetchBrokers', () => brokerApi.fetchBrokers());
Cypress.Commands.add('fetchBroker', brokerClusterName =>
  brokerApi.fetchBroker(brokerClusterName),
);
Cypress.Commands.add('startBroker', brokerClusterName =>
  brokerApi.startBroker(brokerClusterName),
);
Cypress.Commands.add('stopBroker', brokerClusterName =>
  brokerApi.stopBroker(brokerClusterName),
);
Cypress.Commands.add('deleteBroker', brokerClusterName =>
  brokerApi.deleteBroker(brokerClusterName),
);

// Worker API
Cypress.Commands.add('fetchWorker', name => workerApi.fetchWorker(name));
Cypress.Commands.add('fetchWorkers', () => workerApi.fetchWorkers());
Cypress.Commands.add('createWorker', params => workerApi.createWorker(params));

Cypress.Commands.add('startWorker', workerClusterName =>
  workerApi.startWorker(workerClusterName),
);
Cypress.Commands.add('stopWorker', workerClusterName =>
  workerApi.stopWorker(workerClusterName),
);
Cypress.Commands.add('deleteWorker', workerClusterName =>
  workerApi.deleteWorker(workerClusterName),
);

// Property API
Cypress.Commands.add('deleteProperty', (group, name) =>
  streamApp.deleteProperty(group, name),
);
Cypress.Commands.add('startStreamApp', (group, name) =>
  streamApp.startStreamApp(group, name),
);
Cypress.Commands.add('stopStreamApp', (group, name) =>
  streamApp.stopStreamApp(group, name),
);
Cypress.Commands.add('updateProperty', params =>
  streamApp.updateProperty(params),
);

Cypress.Commands.add('fetchProperty', (group, name) =>
  streamApp.fetchProperty(group, name),
);

Cypress.Commands.add('createProperty', params =>
  streamApp.createProperty(params),
);

// Stream API
Cypress.Commands.add('deleteStreamAppJar', name => streamApp.deleteJar(name));
Cypress.Commands.add('fetchStreamAppJars', workerClusterName =>
  streamApp.fetchJars(workerClusterName),
);
Cypress.Commands.add('uploadStreamAppJar', params => {
  const { jarName, workerClusterName } = params;
  cy.fixture(`streamApp/${jarName}`, 'base64')
    .then(Cypress.Blob.base64StringToBlob)
    .then(blob => {
      const type = 'application/java-archive';
      const url = '/api/stream/jars';
      const config = {
        headers: {
          'content-type': 'multipart/form-data',
        },
      };
      const testFile = new File([blob], jarName, { type });
      const dataTransfer = new DataTransfer();
      dataTransfer.items.add(testFile);
      blob = dataTransfer.files;
      let formData = new FormData();
      formData.append('streamapp', blob[0]);
      formData.append('cluster', workerClusterName);
      return axiosInstance.post(url, formData, config);
    });
});

// Topic API
Cypress.Commands.add('fetchTopics', () => topicApi.fetchTopics());
Cypress.Commands.add('fetchTopic', (group, name) =>
  topicApi.fetchTopic(group, name),
);
Cypress.Commands.add('createTopic', params => topicApi.createTopic(params));
Cypress.Commands.add('deleteTopic', (group, name) =>
  topicApi.deleteTopic(group, name),
);
Cypress.Commands.add('startTopic', (group, name) =>
  topicApi.startTopic(group, name),
);
Cypress.Commands.add('stopTopic', (group, name) =>
  topicApi.stopTopic(group, name),
);

// Connector API
Cypress.Commands.add('deleteConnector', (group, name) =>
  connectorApi.deleteConnector(group, name),
);
Cypress.Commands.add('stopConnector', (group, name) =>
  connectorApi.stopConnector(group, name),
);
Cypress.Commands.add('startConnector', (group, name) =>
  connectorApi.startConnector(group, name),
);
Cypress.Commands.add('updateConnector', params =>
  connectorApi.updateConnector(params),
);
Cypress.Commands.add('fetchConnector', (group, name) =>
  connectorApi.fetchConnector(group, name),
);
Cypress.Commands.add('createConnector', params =>
  connectorApi.createConnector(params),
);

// Pipeline API
Cypress.Commands.add('deletePipeline', (group, name) =>
  pipelineApi.deletePipeline(group, name),
);
Cypress.Commands.add('updatePipeline', params =>
  pipelineApi.updatePipeline(params),
);
Cypress.Commands.add('fetchPipelines', () => pipelineApi.fetchPipelines());
Cypress.Commands.add('fetchPipeline', (group, name) =>
  pipelineApi.fetchPipeline(group, name),
);
Cypress.Commands.add('createPipeline', params =>
  pipelineApi.createPipeline(params),
);

Cypress.Commands.add('fetchNodes', () => nodeApi.fetchNodes());

Cypress.Commands.add('updateNode', params => nodeApi.updateNode(params));

Cypress.Commands.add('createNode', params => nodeApi.createNode(params));

Cypress.Commands.add('fetchJars', group => fetchJars(group));
Cypress.Commands.add('createJar', (jarName, group) => {
  cy.fixture(`plugin/${jarName}`, 'base64')
    .then(Cypress.Blob.base64StringToBlob)
    .then(blob => {
      const type = 'application/java-archive';
      const url = '/api/files';
      const config = {
        headers: {
          'content-type': 'multipart/form-data',
        },
      };
      const testFile = new File([blob], jarName, { type });
      const dataTransfer = new DataTransfer();
      dataTransfer.items.add(testFile);
      blob = dataTransfer.files;
      let formData = new FormData();
      formData.append('file', blob[0]);
      formData.append('group', group);
      formData.append('tags', `{"name":"${jarName}"}`);
      return axiosInstance.post(url, formData, config);
    });
});

// Validate API
Cypress.Commands.add('validateConnector', params => validateConnector(params));

// Info API
Cypress.Commands.add('fetchInfo', () => fetchInfo());

// Log API
Cypress.Commands.add('fetchLogs', (serviceName, clusterName) =>
  fetchLogs(serviceName, clusterName),
);

// Utility commands
Cypress.Commands.add('deleteAllServices', () => {
  cy.request('GET', 'api/stream').then(response => {
    const { body: streams } = response;

    if (!isEmpty(streams)) {
      streams.forEach(stream => {
        const { group, state, settings } = stream;
        const { name } = settings;

        if (!isUndefined(state)) {
          cy.stopStreamApp(group, name);
        }
        cy.deleteProperty(group, name);
      });
    }
  });

  cy.fetchJars('default').then(response => {
    const { result: files } = response.data;

    if (!isEmpty(files)) {
      files.forEach(file => {
        cy.request('DELETE', `api/files/${file.name}?group=${file.group}`);
      });
    }
  });

  cy.fetchTopics().then(response => {
    const { result: topics } = response.data;

    if (!isEmpty(topics)) {
      topics.forEach(topic => {
        const { group, state, settings } = topic;
        const { name } = settings;

        if (!isUndefined(state)) {
          cy.stopTopic(group, name);
        }

        cy.deleteTopic(group, name);
      });
    }
  });

  cy.fetchWorkers().then(response => {
    const { result: workers } = response.data;

    if (!isEmpty(workers)) {
      workers.forEach(worker => {
        const { state, settings } = worker;
        const { name } = settings;

        if (!isUndefined(state)) {
          cy.stopWorker(name);
        }

        cy.request('DELETE', `api/workers/${name}`);
      });
    }
  });

  cy.fetchBrokers().then(response => {
    const { result: brokers } = response.data;

    if (!isEmpty(brokers)) {
      brokers.forEach(broker => {
        const { state, settings } = broker;
        const { name } = settings;

        if (!isUndefined(state)) {
          cy.stopBroker(name);
        }

        cy.deleteBroker(name);
      });
    }
  });

  cy.fetchZookeepers().then(response => {
    const { result: zookeepers } = response.data;

    if (!isEmpty(zookeepers)) {
      zookeepers.forEach(zookeeper => {
        const { state, settings } = zookeeper;
        const { name } = settings;

        if (!isUndefined(state)) {
          cy.stopZookeeper(name);
        }

        cy.deleteZookeeper(name);
      });
    }
  });
});
