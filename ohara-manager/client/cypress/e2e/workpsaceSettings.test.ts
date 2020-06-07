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

// Note: Do not change the usage of absolute path
// unless you have a solution to resolve TypeScript + Coverage
import * as generate from '../../src/utils/generate';
import { hashByGroupAndName } from '../../src/utils/sha';
import { deleteAllServices } from '../utils';

const nodeHost = Cypress.env('nodeHost');

describe('Workspace Settings', () => {
  before(() => deleteAllServices());

  it('topics operations of Settings', () => {
    cy.createWorkspace({});

    const sharedTopicName = generate.serviceName({ prefix: 'topic' });
    // click the settings dialog
    cy.findByText('workspace1').click();
    cy.contains('li', 'Settings').click();
    // click the topics button
    cy.findAllByText(/^topics in this workspace$/i).click({ force: true });
    // create topic
    cy.findByTitle('Create Topic').should('be.enabled').click();
    cy.findAllByLabelText('Topic name', { exact: false })
      .filter(':visible')
      .type(sharedTopicName);
    cy.findAllByLabelText('Partitions', { exact: false })
      .filter(':visible')
      .type('1');
    cy.findAllByLabelText('Replication factor', { exact: false })
      .filter(':visible')
      .type('1');
    cy.findAllByText(/^create$/i)
      .filter(':visible')
      .click();
    // the new added topic should exist
    cy.findByText(sharedTopicName).should('exist');
    cy.contains('td', 'RUNNING').should('exist');

    // assert the topic view
    cy.findAllByTitle('View topic').filter(':visible').first().click();
    cy.contains('td', /^state$/i)
      .siblings('td')
      .contains('RUNNING')
      .should('exist')
      // press "ESC" again back to topic list
      .trigger('keydown', { keyCode: 27, which: 27 });

    // the shared topic should be OK to be removed
    cy.findAllByTitle('Delete topic')
      .filter(':visible')
      .first()
      .should('not.be.disabled')
      .click();
    cy.findAllByTestId('confirm-button-DELETE')
      .filter(':visible')
      .first()
      .click();

    // after remove, the topic should not exist
    cy.findAllByText(sharedTopicName).should('not.exist');
  });

  it('zookeeper operations of Settings', () => {
    cy.visit('/');

    // click the settings dialog
    cy.findByText('workspace1').click();
    cy.contains('li', 'Settings').click();
    // click the zookeeper button
    cy.findAllByText(/^zookeeper nodes$/i).click({ force: true });

    cy.findAllByText(nodeHost)
      .should('exist')
      .siblings('td')
      .contains('Available')
      .should('exist');
  });

  it('broker operations of Settings', () => {
    cy.visit('/');

    // click the settings dialog
    cy.findByText('workspace1').click();
    cy.contains('li', 'Settings').click();
    // click the zookeeper button
    cy.findAllByText(/^broker nodes$/i).click({ force: true });

    cy.findAllByText(nodeHost)
      .should('exist')
      .siblings('td')
      .contains('Available')
      .should('exist');
  });

  it('worker operations of Settings', () => {
    cy.visit('/');

    // click the settings dialog
    cy.findByText('workspace1').click();
    cy.contains('li', 'Settings').click();
    // click the zookeeper button
    cy.findAllByText(/^worker nodes$/i).click({ force: true });

    cy.findAllByText(nodeHost)
      .should('exist')
      .siblings('td')
      .contains('Available')
      .should('exist');
  });

  it('nodes operations of Settings', () => {
    cy.visit('/');

    // click the settings dialog
    cy.findByText('workspace1').click();
    cy.contains('li', 'Settings').click();
    // click the zookeeper button
    cy.findAllByText(/^workspace nodes$/i).click({ force: true });

    cy.findAllByText(nodeHost)
      .should('exist')
      .siblings('td')
      .contains('Available')
      .should('exist');
  });

  it('file operations of Settings', () => {
    cy.visit('/');

    // we use the default workspace name and group for file tags
    const workspaceKey = {
      name: 'workspace1',
      group: 'workspace',
    };
    const fileGroup = hashByGroupAndName(workspaceKey.group, workspaceKey.name);

    // create files object
    const source = {
      fixturePath: 'plugin',
      // we use an existing file to simulate upload jar
      name: 'ohara-it-source.jar',
      group: fileGroup,
      tags: { parentKey: workspaceKey },
    };

    const sink = {
      fixturePath: 'plugin',
      // we use an existing file to simulate upload jar
      name: 'ohara-it-sink.jar',
      group: fileGroup,
      tags: { parentKey: workspaceKey },
    };

    const stream = {
      fixturePath: 'stream',
      name: 'ohara-it-stream.jar',
      group: fileGroup,
      tags: { parentKey: workspaceKey },
    };
    const files = [source, sink, stream];

    // click the settings dialog
    cy.findByText('workspace1').click();
    cy.contains('li', 'Settings').click();
    // click the file button
    cy.findAllByText(/^files in this workspace$/i).click({ force: true });

    // upload the files by custom command "createJar"
    cy.findAllByText('Files in this workspace')
      .filter(':visible')
      .parent('div')
      .siblings('div')
      .first()
      .within(() => {
        files.forEach((file) => {
          cy.get('input[type="file"]').then((element) => {
            cy.createJar(file).then((params) => {
              (element[0] as HTMLInputElement).files = params.fileList;
              cy.wrap(element).trigger('change', { force: true });
            });
          });
        });
      });

    // after upload file, click the upload file again
    cy.wait(1000);
    cy.findAllByTitle('Upload File').first().click();

    cy.findByText(source.name).should('exist');
    cy.findByText(sink.name).should('exist');
    cy.findByText(stream.name).should('exist');

    // check the source file could be removed
    cy.findByText(source.name)
      .siblings('td')
      .last()
      .within((el$) => {
        el$.find('div[title="Delete file"]').click();
      });
    // confirm dialog
    cy.findByTestId('confirm-button-DELETE').click();

    // after removed, the file should not be existed
    cy.findByText(source.name).should('not.exist');

    // //filter
    cy.findAllByPlaceholderText('Search').filter(':visible').type(stream.name);
    cy.findByText(stream.name).should('exist');
    cy.findByText(sink.name).should('not.exist');

    // view the classNames of stream file
    cy.findByText(stream.name)
      .siblings('td')
      .last()
      .within((el$) => {
        el$.find('div[title="View file"]').click();
      });
    cy.findAllByText('DumbStream', { exact: false }).should('exist');
  });
});
