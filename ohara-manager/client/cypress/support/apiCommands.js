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
import { fetchContainers } from '../../src/api/containerApi';

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
Cypress.Commands.add('testCreateWorker', params =>
  workerApi.createWorker(params),
);

Cypress.Commands.add('startWorker', workerClusterName =>
  workerApi.startWorker(workerClusterName),
);
Cypress.Commands.add('stopWorker', workerClusterName =>
  workerApi.stopWorker(workerClusterName),
);

// Property API
Cypress.Commands.add('deleteProperty', params =>
  streamApp.deleteProperty(params),
);
Cypress.Commands.add('startStreamApp', name => streamApp.startStreamApp(name));
Cypress.Commands.add('stopStreamApp', name => streamApp.stopStreamApp(name));
Cypress.Commands.add('updateProperty', params =>
  streamApp.updateProperty(params),
);

Cypress.Commands.add('fetchProperty', name => streamApp.fetchProperty(name));

Cypress.Commands.add('createProperty', params =>
  streamApp.createProperty(params),
);

// Stream API
Cypress.Commands.add('deleteStreamAppJar', name => streamApp.deleteJar(name));
Cypress.Commands.add('fetchStreamAppJars', workerClusterName =>
  streamApp.fetchJars(workerClusterName),
);
Cypress.Commands.add('testUploadStreamAppJar', params => {
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
      const testFile = new File([blob], jarName, { type: type });
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
Cypress.Commands.add('fetchTopic', name => topicApi.fetchTopic(name));
Cypress.Commands.add('testCreateTopic', params => topicApi.createTopic(params));
Cypress.Commands.add('testDeleteTopic', name => topicApi.deleteTopic(name));
Cypress.Commands.add('startTopic', name => topicApi.startTopic(name));
Cypress.Commands.add('stopTopic', name => topicApi.stopTopic(name));

// Connector API
Cypress.Commands.add('deleteConnector', name =>
  connectorApi.deleteConnector(name),
);
Cypress.Commands.add('stopConnector', name => connectorApi.stopConnector(name));
Cypress.Commands.add('startConnector', name =>
  connectorApi.startConnector(name),
);
Cypress.Commands.add('updateConnector', params =>
  connectorApi.updateConnector(params),
);
Cypress.Commands.add('fetchConnector', name =>
  connectorApi.fetchConnector(name),
);
Cypress.Commands.add('createConnector', params =>
  connectorApi.createConnector(params),
);

// Pipeline API
Cypress.Commands.add('testDeletePipeline', name =>
  pipelineApi.deletePipeline(name),
);
Cypress.Commands.add('updatePipeline', params =>
  pipelineApi.updatePipeline(params),
);
Cypress.Commands.add('fetchPipelines', () => pipelineApi.fetchPipelines());
Cypress.Commands.add('fetchPipeline', name => pipelineApi.fetchPipeline(name));
Cypress.Commands.add('testCreatePipeline', params =>
  pipelineApi.createPipeline(params),
);

Cypress.Commands.add('fetchNodes', () => nodeApi.fetchNodes());

Cypress.Commands.add('updateNode', params => nodeApi.updateNode(params));

Cypress.Commands.add('createNode', params => nodeApi.createNode(params));

Cypress.Commands.add('fetchJars', group => fetchJars(group));
Cypress.Commands.add('createJar', jarName => {
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
      const testFile = new File([blob], jarName, { type: type });
      const dataTransfer = new DataTransfer();
      dataTransfer.items.add(testFile);
      blob = dataTransfer.files;
      let formData = new FormData();
      formData.append('file', blob[0]);
      return axiosInstance.post(url, formData, config);
    });
});

// Container API
Cypress.Commands.add('fetchContainers', name => fetchContainers(name));

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
        cy.request('DELETE', `api/stream/${stream.name}`);
      });
    }
  });

  cy.fetchJars('default').then(response => {
    const { result: files } = response.data;

    if (!isEmpty(files)) {
      files.forEach(file => {
        cy.request('DELETE', `api/files/${file.name}?group=default`);
      });
    }
  });

  cy.fetchWorkers().then(response => {
    const { result: workers } = response.data;

    if (!isEmpty(workers)) {
      workers.forEach(worker => {
        if (!isUndefined(worker.state)) {
          cy.stopWorker(worker.name);
        }
        cy.request('DELETE', `api/workers/${worker.name}`);
      });
    }
  });

  cy.fetchTopics().then(response => {
    const { result: topics } = response.data;

    if (!isEmpty(topics)) {
      topics.forEach(topic => {
        if (!isUndefined(topic.state)) {
          cy.stopTopic(topic.name);
        }

        cy.testDeleteTopic(topic.name);
      });
    }
  });

  cy.fetchBrokers().then(response => {
    const { result: brokers } = response.data;

    if (!isEmpty(brokers)) {
      brokers.forEach(broker => {
        if (!isUndefined(broker.state)) {
          cy.stopBroker(broker.name);
        }

        cy.deleteBroker(broker.name);
      });
    }
  });

  cy.fetchZookeepers().then(response => {
    const { result: zookeepers } = response.data;

    if (!isEmpty(zookeepers)) {
      zookeepers.forEach(zookeeper => {
        if (!isUndefined(zookeeper.state)) {
          cy.stopZookeeper(zookeeper.name);
        }

        cy.deleteZookeeper(zookeeper.name);
      });
    }
  });
});
