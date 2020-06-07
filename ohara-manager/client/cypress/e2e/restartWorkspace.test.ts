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
import { hashByGroupAndName } from '../../src/utils/sha';
import { deleteAllServices } from '../utils';

describe('RestartWorkspace', () => {
  beforeEach(async () => await deleteAllServices());

  it('restart a workspace normally should be worked', () => {
    // create workspace
    cy.createWorkspace({});

    // click the settings dialog
    cy.findByText('workspace1').click();
    cy.contains('li', 'Settings').click();

    // click upload jars dialog
    cy.findByText('Worker plugins and shared jars').click();
    // click upload plugins
    cy.findAllByTitle('Add File').first().click();
    cy.findAllByTitle('Upload File').filter(':visible');

    // upload the file by custom command "createJar"
    const workspaceKey = {
      name: 'workspace1',
      group: 'workspace',
    };
    const fileGroup = hashByGroupAndName(workspaceKey.group, workspaceKey.name);
    const source = {
      fixturePath: 'plugin',
      name: 'ohara-it-source.jar',
      group: fileGroup,
    };
    cy.findByText('Select file')
      .parent('div')
      .siblings('div')
      .first()
      .find('input[type="file"]')
      .then((element) => {
        cy.createJar(source).then((params) => {
          (element[0] as HTMLInputElement).files = params.fileList;
          cy.wrap(element).trigger('change', { force: true });
        });
      });

    // after upload file, click the upload file again
    cy.wait(1000);
    cy.findAllByTitle('Upload File').first().click();

    // select the uploaded file
    cy.findByText('ohara-it-source.jar')
      .siblings('td')
      .eq(0)
      .find('input')
      .check();

    // click save button
    cy.findAllByText('Save').filter(':visible').click();

    // click the arrow button back to Settings dialog
    cy.findAllByText('Worker plugins and shared jars')
      .filter(':visible')
      .siblings('button')
      .first()
      .click();

    // click the restart workspace button
    cy.findByText('Restart this workspace').click();
    cy.findAllByText('Restart').filter(':visible').click();

    // wait and click button back to homepage
    cy.findAllByText('CLOSE')
      .first()
      .parent('button')
      .should('be.enabled')
      .click();

    // TODO: enable the following lines after #4874 merged
    // cy.findByTestId('edit-workspace-dialog-close-button')
    //   .should('be.disabled')
    //   .click();
    cy.findByTestId('edit-workspace-dialog-close-button').click({
      force: true,
    });

    // Add new pipeline
    cy.findByText(/^pipelines$/i)
      .siblings('svg')
      .first()
      .click();
    cy.findByText(/^add a new pipeline$/i).should('exist');

    cy.findByTestId('new-pipeline-dialog').find('input').type('pipeline1');

    cy.findByText(/^add$/i).click();

    // check the toolbox
    cy.findByText(/^toolbox$/i).should('exist');

    cy.findByText(/^source$/i)
      .should('exist')
      .click();

    cy.findByText('IncludeAllTypesSourceConnector').should('exist');
  });
});
