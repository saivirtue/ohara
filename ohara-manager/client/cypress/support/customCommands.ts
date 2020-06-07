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

/* eslint-disable no-unused-vars */
/* eslint-disable @typescript-eslint/no-unused-vars */
/* eslint-disable no-console */

// Note: Do not change the usage of absolute path
// unless you have a solution to resolve TypeScript + Coverage
import '@testing-library/cypress/add-commands';
import { capitalize } from 'lodash';

import { KIND, CELL_TYPES } from '../../src/const';
import * as connectorApi from '../../src/api/connectorApi';
import { NodeResponse } from '../../src/api/apiInterface/nodeInterface';
import { ClusterResponse } from '../../src/api/apiInterface/clusterInterface';
import { TopicResponse } from '../../src/api/apiInterface/topicInterface';
import { SOURCES, SINKS } from '../../src/api/apiInterface/connectorInterface';
import { hashByGroupAndName } from '../../src/utils/sha';
import * as generate from '../../src/utils/generate';
import { sleep } from '../../src/utils/common';

interface FixtureResponse {
  name: string;
  fileList: FileList;
  file: File;
  group: string;
  tags?: object;
}

declare global {
  namespace Cypress {
    type FixtureRequest = {
      fixturePath: string;
      name: string;
      group: string;
      tags?: object;
    };
    type ServiceData = {
      workspaceName?: string;
      node?: NodeResponse;
      zookeeper?: ClusterResponse;
      broker?: ClusterResponse;
      worker?: ClusterResponse;
      topic?: TopicResponse;
    };

    interface Chainable {
      // Utils
      createJar: (file: FixtureRequest) => Promise<FixtureResponse>;
      createWorkspace: ({
        workspaceName,
      }: {
        workspaceName?: string;
      }) => Chainable<null>;
      produceTopicData: (
        workspaceName?: string,
        topicName?: string,
      ) => Chainable<void>;
      // Drag & Drop
      dragAndDrop: (
        shiftX: number,
        shiftY: number,
      ) => Chainable<JQuery<HTMLElement>>;
      addNode: () => Chainable<null>;
      addElement: (
        name: string,
        kind: string,
        className?: string,
      ) => Chainable<null>;
      getCell: (name: string) => Chainable<HTMLElement>;
      cellAction: (name: string, action: string) => Chainable<HTMLElement>;
    }
  }
}

const workspaceGroup = 'workspace';
const workerGroup = 'worker';

const nodeHost = Cypress.env('nodeHost');
const nodePort = Cypress.env('nodePort');
const nodeUser = Cypress.env('nodeUser');
const nodePass = Cypress.env('nodePass');

const sources = Object.values(SOURCES).sort();
const sinks = Object.values(SINKS).sort();

// Utility commands
Cypress.Commands.add('createJar', (file: Cypress.FixtureRequest) => {
  const { fixturePath, name, group: jarGroup, tags: jarTags } = file;
  cy.fixture(`${fixturePath}/${name}`, 'base64')
    .then(Cypress.Blob.base64StringToBlob)
    .then((blob) => {
      const blobObj = blob as Blob;
      const type = 'application/java-archive';
      const testFile = new File([blobObj], name, { type });
      const dataTransfer = new DataTransfer();
      dataTransfer.items.add(testFile);
      const fileList = dataTransfer.files;
      const params: FixtureResponse = {
        name,
        fileList,
        file: fileList[0],
        group: jarGroup,
      };
      if (jarTags) params.tags = jarTags;
      return params;
    });
});
Cypress.Commands.add(
  'createWorkspace',
  ({
    workspaceName,
  }: {
    workspaceName?: string;
  } = {}) => {
    Cypress.Commands.add('addNode', () => {
      cy.get('body').then(($body) => {
        const isDockerMode =
          $body.find('span > button[title="Create Node"]').length > 0;

        if (isDockerMode) {
          cy.get('body').then(($body) => {
            // the node has not been added yet, added directly
            if ($body.find(`td:contains(${nodeHost})`).length === 0) {
              cy.findByTitle('Create Node').click();
              cy.get('input[name=hostname]').type(nodeHost);
              cy.get('input[name=port]').type(nodePort);
              cy.get('input[name=user]').type(nodeUser);
              cy.get('input[name=password]').type(nodePass);
              cy.findByText(/^create$/i).click();
            }
            cy.findByText(nodeHost)
              .siblings('td')
              .find('input[type="checkbox"]')
              .click()
              .findByText(/^save$/i)
              .click();
            cy.findAllByText(/^next$/i)
              .filter(':visible')
              .click();
          });
        } else {
          cy.findByText(nodeHost)
            .siblings('td')
            .find('input[type="checkbox"]')
            .click()
            .findByText(/^save$/i)
            .click();
          cy.findAllByText(/^next$/i)
            .filter(':visible')
            .click();
        }
        cy.end();
      });
    });

    // Click the quickstart dialog
    cy.visit('/');

    // Since we will redirect the url
    // need to wait a little time for url applying
    cy.wait(2000);

    cy.location().then((location) => {
      if (location.pathname === '/') {
        // first time in homepage, close the helper quickMode dialog
        cy.findByTestId('close-intro-button').click();
      }
      cy.findByTitle('Create a new workspace').click();
      cy.findByText(/^quick create$/i)
        .should('exist')
        .click();
    });

    // Step1: workspace name
    if (workspaceName) {
      // type the workspaceName by parameter
      cy.findByDisplayValue('workspace', { exact: false })
        .clear()
        .type(workspaceName);
    }
    cy.findAllByText(/^next$/i)
      .filter(':visible')
      .click();

    // Step2: select nodes
    // we wait a little time for the "click button" to be rendered
    cy.wait(1000);
    cy.contains('p:visible', 'Click here to select nodes').click();
    cy.addNode();

    // Step3: create workspace
    cy.wait(1000);
    cy.findAllByText(/^submit$/i)
      .filter(':visible')
      .click();

    cy.findByTitle('Workspace list').children().should('not.be.disabled');

    cy.end();
  },
);

// Note: this custom command is a "heavy" command, may take almost 40 seconds to accomplish
// make sure you have set enough timeout of defaultCommandTimeout in cypress.e2e.json
Cypress.Commands.add(
  'produceTopicData',
  async (workspaceName?: string, topicName?: String) => {
    if (!workspaceName || !topicName) {
      console.error('the workspaceName and topicName are required fields');
      return;
    }
    const connector = {
      name: generate.serviceName({ prefix: 'perf' }),
      // it is ok to use random group here; we just need a temp connector to produce data
      group: generate.serviceName({ prefix: 'group' }),
      connector__class: SOURCES.perf,
      topicKeys: [
        {
          name: topicName,
          group: hashByGroupAndName(workspaceGroup, workspaceName),
        },
      ],
      workerClusterKey: {
        name: workspaceName,
        group: workerGroup,
      },
    };

    await connectorApi.create(connector);
    await connectorApi.start(connector);

    // sleep 10 seconds for perf connector to write some data to topic
    await sleep(10000);

    // remove this temp connector
    await connectorApi.forceStop(connector);
    await connectorApi.remove(connector);
  },
);

// Drag & Drop
Cypress.Commands.add(
  'dragAndDrop',
  { prevSubject: true },
  (subject: HTMLElement, shiftX: number, shiftY: number) => {
    cy.wrap(subject)
      // using the top-left position to trigger the event
      // since we calculate the moving event by rect.left and rect.top
      .trigger('mousedown', 'topLeft', { timeout: 1000, which: 1 });
    // we only get one "flying element" at one time
    // it's ok to find by testid
    cy.findByTestId('flying-element').then((element) => {
      cy.wrap(element)
        .trigger('mousemove', 'topLeft', {
          timeout: 1000,
          pageX: shiftX,
          pageY: shiftY,
          force: true,
        })
        .trigger('mouseup', 'topLeft', { timeout: 1000, force: true });
    });
  },
);
Cypress.Commands.add('addElement', (name, kind, className) => {
  cy.log(`add element: ${name} of ${kind} with className ${className}`);
  // toolbox: 272 width + navigator: 240 width + appBar: 64 width, we need to avoid covering it
  const initialX = 600;
  // the controllers tab has approximate 72 height, we need to avoid covering it
  const initialY = 100;
  const shiftWidth = 350;
  const shiftHeight = 110;

  cy.get('body').then(($body) => {
    let size = 0;
    cy.log(
      'calculate the size of elements(source, sink, stream, topic) in pipeline',
    );
    if ($body.find('div.topic').length > 0)
      size = size + $body.find('div.topic').length;
    if ($body.find('div.connector').length > 0)
      size = size + $body.find('div.connector').length;

    cy.findByText(capitalize(kind)).should('exist').click();

    // re-render the cell position to maximize the available space
    // the view of cells will be a [n, 2] matrix
    const x = size % 2 === 0 ? initialX : initialX + shiftWidth;
    const y = initialY + ~~(size / 2) * shiftHeight;
    cy.log(`element position: ${x}, ${y}`);

    // wait a little time for the toolbox list rendered
    cy.wait(2000);

    if (kind === KIND.source || kind === KIND.sink) {
      const elementIndex =
        kind === KIND.source
          ? sources.indexOf(className)
          : sinks.indexOf(className);

      cy.findByTestId('toolbox-draggable')
        .find(`g[data-type="${CELL_TYPES.ELEMENT}"]:visible`)
        // the element index to be added
        .eq(elementIndex)
        .dragAndDrop(x, y);

      // type the name and add
      cy.findByLabelText(`${capitalize(kind)} name`, { exact: false }).type(
        name,
      );
      cy.findAllByText(/^add$/i).filter(':visible').click();
    } else if (kind === KIND.topic) {
      if (!name.startsWith('T')) {
        cy.findByText(name).should('exist');

        let topics: string[] = [];
        $body
          .find('#topic-list')
          .find('span.display-name')
          .each(function (_, element) {
            if (element.textContent) {
              if (element.textContent === 'Pipeline Only')
                // make sure the "pipeline only" topic is in first order
                topics.push('_private');
              topics.push(element.textContent);
            }
          });
        cy.findByTestId('toolbox-draggable')
          .find(`g[data-type="${CELL_TYPES.ELEMENT}"]:visible`)
          // the element index to be added
          .eq(topics.sort().indexOf(name) - 1)
          .dragAndDrop(x, y);
      } else {
        // create a pipeline-only topic
        cy.findByTestId('toolbox-draggable')
          .find(`g[data-type="${CELL_TYPES.ELEMENT}"]:visible`)
          // the only "draggable" cell is pipeline-only topic
          .first()
          .dragAndDrop(x, y);
      }
    } else if (kind === KIND.stream) {
      cy.findByTestId('toolbox-draggable')
        .find(`g[data-type="${CELL_TYPES.ELEMENT}"]:visible`)
        // we only got 1 class for the uploaded stream jar
        // it's ok to assert the first element is the "stream class"
        .eq(0)
        .dragAndDrop(x, y);

      // type the name and add
      cy.findByLabelText(`${capitalize(kind)} name`, { exact: false }).type(
        name,
      );
      cy.findAllByText(/^add$/i).filter(':visible').click();
    }

    // wait a little time for the cell added
    cy.wait(3000);

    // close this panel
    cy.findByText(capitalize(kind)).click();
    cy.end();
  });
});

Cypress.Commands.add('getCell', (name) => {
  // open the cell menu
  cy.findAllByText(name)
    .filter(':visible')
    .should('exist')
    .parents(
      name.startsWith('topic') || name.startsWith('T')
        ? 'div.topic'
        : 'div.connector',
    )
    .first()
    .then((el) => {
      const testId = el[0].getAttribute('data-testid');
      return cy.get(`g[model-id="${testId}"]`);
    });
});

Cypress.Commands.add('cellAction', (name, action) => {
  // open the cell menu
  cy.findAllByText(name)
    .filter(':visible')
    .should('exist')
    .parents(
      name.startsWith('topic') || name.startsWith('T')
        ? 'div.topic'
        : 'div.connector',
    )
    .first()
    .within(() => {
      cy.get(`button.${action}:visible`);
    });
});
