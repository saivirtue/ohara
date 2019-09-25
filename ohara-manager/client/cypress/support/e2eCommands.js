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

import '@testing-library/cypress/add-commands';
import { isEmpty, get } from 'lodash';

import * as utils from '../utils';
import * as generate from '../../src/utils/generate';
import { axiosInstance } from '../../src/api/apiUtils';

Cypress.Commands.add('addWorker', params => {
  const jarName = get(params, 'jarName', undefined);
  let jarKeys;
  const { name: nodeName } = utils.getFakeNode();
  const prefix = Cypress.env('servicePrefix');
  const workerName = get(
    params,
    'workerName',
    generate.serviceName({
      prefix: `${prefix}wk`,
      length: 3,
    }),
  );

  if (jarName !== undefined) {
    cy.uploadTestPlugin({ jarName, workerClusterName: workerName });
    jarKeys = [{ name: jarName, group: workerName }];
  }

  // Store the worker name in the Cypress env
  // as we'll be using it throughout the tests
  Cypress.env('WORKER_NAME', workerName);

  cy.request('GET', 'api/brokers')
    .then(res => res.body[0])
    .as('broker');

  cy.get('@broker').then(broker => {
    cy.request('GET', `api/zookeepers/${broker.zookeeperClusterName}`)
      .then(res => res.body)
      .as('zookeepers');
  });

  cy.get('@zookeepers').then(zookeepers => {
    cy.get('@broker').then(broker => {
      cy.request('POST', 'api/workers', {
        name: workerName,
        brokerClusterName: broker.settings.name,
        jarKeys,
        groupId: generate.id(),
        nodeNames: [nodeName],
        tags: {
          broker: {
            name: broker.settings.name,
            imageName: broker.settings.imageName,
          },
          zookeeper: {
            name: zookeepers.settings.name,
            imageName: zookeepers.settings.imageName,
          },
        },
      });
      cy.request('PUT', `api/workers/${workerName}/start`);
    });
  });

  let count = 0;
  const max = 20;
  // Make a request to configurator see if worker cluster is ready for use
  const req = endPoint => {
    cy.request('GET', endPoint).then(response => {
      // Wait until the connectors are loaded in the worker we just created
      const workerIsReady = !isEmpty(response.body.connectors);

      if (workerIsReady || count > max) return;

      // if worker is not ready yet, wait for 2 sec and make another request
      count++;
      cy.wait(2000);
      req(endPoint);
    });
  };

  const endPoint = `api/workers/${workerName}`;
  cy.request('GET', endPoint).then(() => req(endPoint));
});

Cypress.Commands.add('addPipeline', params => {
  cy.request('POST', `/api/pipelines`, params).then(({ body }) => body);
});

Cypress.Commands.add('putPipeline', params => {
  const { url, param } = params;
  cy.request('PUT', `/api/pipelines${url}`, param).then(({ body }) => body);
});

Cypress.Commands.add('addConnector', params => {
  cy.request('POST', `/api/connectors`, params).then(({ body }) => body);
});

Cypress.Commands.add('putConnector', params => {
  const { url, param } = params;
  cy.request('PUT', `/api/connectors${url}`, param).then(({ body }) => body);
});

Cypress.Commands.add(
  'addTopic',
  (
    topicName = generate.serviceName({ prefix: 'topic' }),
    workerName = Cypress.env('WORKER_NAME'),
  ) => {
    cy.request('GET', 'api/workers')
      .then(res => {
        // Make sure we're getting the right broker cluster name here
        const workers = res.body;
        const currentWorkerName = workerName;
        const worker = workers.find(
          worker => worker.settings.name === currentWorkerName,
        );

        return worker.brokerClusterName;
      })
      .as('brokerClusterName');

    const group = `${Cypress.env('WORKER_NAME')}`;
    cy.get('@brokerClusterName').then(brokerClusterName => {
      cy.request('POST', '/api/topics', {
        name: topicName,
        numberOfReplications: 1,
        numberOfPartitions: 1,
        brokerClusterName,
        group,
      }).then(({ body }) => body);
    });

    cy.request('PUT', `/api/topics/${topicName}/start?group=${group}`);
    Cypress.env('TOPIC_NAME', topicName);
  },
);

Cypress.Commands.add('removeWorkers', () => {
  // Get workers that are started by this test run
  // via the `servicePrefix`
  cy.request('GET', 'api/workers').then(response => {
    const servicePrefix = Cypress.env('servicePrefix');
    const workers = response.body.filter(worker =>
      worker.settings.name.includes(servicePrefix),
    );

    if (isEmpty(workers)) return;
    workers.forEach(worker => {
      const { name } = worker;

      // Make a request to stop the worker and wait until the
      // worker is stopped
      cy.request('PUT', `api/workers/${name}/stop`);

      let count = 0;
      const max = 10;
      const req = endPoint => {
        cy.request('GET', endPoint).then(res => {
          // If the `state` is not present, then the worker
          // is safe to be deleted
          const workerIsStopped = res.body.state === undefined;

          if (workerIsStopped || count > max) return;

          // Wait a bit longer and see if worker is stopped
          count++;
          cy.wait(2000);
          req(endPoint);
        });
      };

      const endPoint = `api/workers/${name}`;
      cy.request('GET', endPoint).then(() => req(endPoint));

      // Since the worker is stopped at this point, we can safely
      // delete the service
      cy.request('DELETE', `api/workers/${name}`).then(() =>
        utils.recursiveDeleteWorker('api/workers', name),
      );
    });
  });
});

Cypress.Commands.add('uploadTestStreamAppJar', workerClusterName => {
  cy.fixture(`streamApp/ohara-streamapp.jar`, 'base64')
    .then(Cypress.Blob.base64StringToBlob)
    .then(blob => {
      const type = 'application/java-archive';
      const url = '/api/files';
      const config = {
        headers: {
          'content-type': 'multipart/form-data',
        },
      };
      const testFile = new File([blob], 'ohara-streamapp.jar', { type });
      const dataTransfer = new DataTransfer();
      dataTransfer.items.add(testFile);
      blob = dataTransfer.files;

      let formData = new FormData();
      formData.append('file', blob[0]);
      formData.append('group', workerClusterName);
      formData.append('tags', '{"type":"streamjar"}');
      const res = axiosInstance.post(url, formData, config);
      cy.log(res);
    });
});

Cypress.Commands.add('uploadTestPlugin', params => {
  const { jarName, workerClusterName } = params;
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
      formData.append('group', workerClusterName);
      formData.append('tags', '{"type":"plugin"}');
      axiosInstance.post(url, formData, config);
    });
});

Cypress.Commands.add('deleteTestPlugin', params => {
  const { jarName, workerName } = params;
  cy.request('DELETE', `api/files/${jarName}?group=${workerName}`);
});

Cypress.Commands.add('uploadJar', (selector, fixturePath, name, type) => {
  cy.get(selector).then(subject =>
    cy.window().then(win =>
      cy
        .fixture(fixturePath, 'base64')
        .then(Cypress.Blob.base64StringToBlob)
        .then(blob => {
          const el = subject[0];
          const testFile = new win.File([blob], name, {
            type,
          });
          const dataTransfer = new win.DataTransfer();
          dataTransfer.items.add(testFile);
          el.files = dataTransfer.files;
          cy.wrap(subject).trigger('change', { force: true });
        }),
    ),
  );
});
