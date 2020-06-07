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
import { KIND } from '../../src/const';
import * as generate from '../../src/utils/generate';
import { deleteAllServices } from '../utils';

describe('DevToolDialog - Topics tab', () => {
  beforeEach(async () => await deleteAllServices());

  it('we should have empty topic list initially', () => {
    // Close the quickstart dialog
    cy.visit('/');
    cy.findByTestId('close-intro-button').click();

    // App bar is visible
    cy.findByTitle('Create a new workspace').should('be.visible');

    cy.findByTitle('Developer Tools').click();
    cy.findByText(/^topics$/i).should('exist');

    // since there are no topics, we should not able to click dropdown list
    cy.findByTestId('devtool-topic-list')
      .find('input')
      .should('not.be.visible');
  });

  it('with an exist workspace, topic list of devTool should work normally', () => {
    const workspaceName = generate.serviceName({ length: 10 });
    const sharedTopicName = generate.serviceName({ prefix: 'topic' });
    cy.createWorkspace({ workspaceName });

    // Create a new topic
    // click the settings dialog
    cy.findByText(workspaceName).click();
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
    cy.contains('td', 'RUNNING')
      .should('exist')
      // press "ESC" again back to homepage
      .trigger('keydown', { keyCode: 27, which: 27 });

    // Add a new pipeline
    // since devTool could only see the topics in pipeline
    cy.findByText(/^pipelines$/i)
      .siblings('svg')
      .first()
      .click()
      .findByText(/^add a new pipeline$/i)
      .should('exist');
    cy.findByTestId('new-pipeline-dialog').find('input').type('pipeline1');
    cy.findByText(/^add$/i).click();

    // check the toolbox
    cy.findByText(/^toolbox$/i).should('exist');
    // add the topic
    cy.addElement(sharedTopicName, KIND.topic);

    cy.produceTopicData(workspaceName, sharedTopicName).visit('/', {
      onBeforeLoad(win) {
        // to surveillance the window.open() event
        // we stub it and do nothing
        cy.stub(win, 'open');
      },
    });

    cy.findAllByText(`${workspaceName}`, { exact: false }).should('exist');

    // Check the topic tab exist
    cy.findByTitle('Developer Tools').click();
    // Check the status bar of no data
    cy.findByText(/^no topic data$/i).should('exist');

    cy.findByTestId('devtool-topic-list').click();
    cy.findAllByText(sharedTopicName).filter('li[role="option"]').click();

    // Check the topic data detail view
    cy.findByTestId('view-topic-table')
      .should('exist')
      .get('tbody tr')
      .should('have.length.greaterThan', 0)
      .findByTestId('detail-view-icon-0')
      .click();

    // the detail view should exist
    cy.findByTestId('topic-detail-view').should('exist').find('button').click();

    // Check the status bar
    cy.findByText(/^10 rows per query$/i).should('exist');

    // refresh button
    cy.findByTitle('Fetch the data again').click().get('table').should('exist');

    // query button
    cy.findByTitle('Query with different parameters')
      .click()
      .findByText(/^query$/i)
      .click()
      .get('table')
      .should('exist');
    cy.findByText(/^rows per query$/i)
      .parent()
      .find('input[type=number]')
      .first()
      .type('{esc}');

    // open new window button
    cy.findByTitle('Open in a new window').click();
    cy.window().its('open').should('be.called');

    // verify the switching tab operations work normally
    cy.findByText(/^logs$/i).click();
    cy.findByTestId('log-type-select').click();
    cy.findByText(/^worker$/i).click();
    cy.findByTestId('view-log-list').should('exist');
    // switch back again should be ok
    cy.findAllByText(/^topics$/i)
      .parents('button')
      .first()
      .click();
    cy.findByTestId('view-topic-table').should('exist');

    // close button
    cy.findByTitle('Close this panel').click();
  });
});

describe('DevToolDialog - Logs tab', () => {
  beforeEach(async () => await deleteAllServices());

  it('with an exist workspace, configurator log of devTool should work normally', () => {
    const workspaceName = generate.serviceName({ length: 10 });
    cy.createWorkspace({ workspaceName });

    cy.visit(`/${workspaceName}`, {
      onBeforeLoad(win) {
        // to surveillance the window.open() event
        // we stub it and do nothing
        cy.stub(win, 'open');
      },
    });

    cy.findAllByText(`${workspaceName}`, { exact: false }).should('exist');

    // Check the log tab exist
    cy.findByTitle('Developer Tools')
      .click()
      .findByText(/^logs$/i)
      .click();

    // Check the status bar of no data
    cy.findByText(/^no log data$/i).should('exist');

    // Check the logType dropdown list
    cy.findByTestId('log-type-select').click();

    // Check the log data of configurator
    cy.findByText(/^configurator$/i).click();
    cy.findByTestId('view-log-list').should('exist');

    // Check the status bar
    cy.findByText(/^latest 10 minutes$/i).should('exist');

    // refresh button
    cy.get('span[title="Fetch the data again"] > button:enabled')
      .click()
      .findByTestId('view-log-list')
      .should('exist');

    // query button
    cy.get(
      'span[title="Query with different parameters"] > button:enabled',
    ).click();
    cy.findByTestId('log-query-popover')
      .find('button')
      .click()
      .findByTestId('view-log-list')
      .should('exist');
    cy.findByText(/^minutes per query$/i)
      .parent()
      .find('input[type=number]')
      .first()
      .type('{esc}');

    // open new window button
    cy.findByTitle('Open in a new window').click();
    cy.window().its('open').should('be.called');

    // close button
    cy.findByTitle('Close this panel').click();
  });
});
