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
import { deleteAllServices } from '../utils';

describe('EventLogDialog', () => {
  before(async () => await deleteAllServices());

  it('event log should be empty after removed', () => {
    // Close the intro dialog
    cy.visit('/');
    cy.findByTestId('close-intro-button').click();

    // App bar is visible
    cy.findByTitle('Create a new workspace').should('be.visible');

    cy.findByTitle('Event logs').click();
    cy.findByText('Event Logs').should('exist');

    // click remove button if there are some logs
    cy.findByTitle('Clear event logs')
      .should('exist')
      .find('button')
      .within(($el) => {
        const isDisabled = $el.is(':disabled');
        if (!isDisabled) {
          cy.wrap($el).click();
        }
      });

    // The event log message should be cleared by now
    cy.findByText('No log').should('exist');
  });

  it('event log should work', () => {
    cy.createWorkspace({});

    cy.findByTitle('Event logs').click();
    cy.findByTestId('event-logs').within(() => {
      cy.findAllByText('Successfully created workspace workspace1.').should(
        'exist',
      );
    });

    cy.findByTitle('Event logs settings').should('exist').click();
    cy.findByPlaceholderText('The maximum amount of logs.')
      .should('exist')
      .clear()
      .type('10');
    cy.findAllByText(/^save$/i)
      .filter(':visible')
      .click();

    // Add new pipeline
    cy.findByText(/^pipelines$/i)
      .siblings('svg')
      .first()
      .click();
    cy.findByText(/^add a new pipeline$/i).should('exist');

    cy.findByTestId('new-pipeline-dialog').find('input').type('pipeline1');

    cy.findByText(/^add$/i).click();

    // the workspace event log should be still existed after create pipeline
    cy.findByTestId('event-logs').within(() => {
      cy.findByText('Successfully created workspace workspace1.').should(
        'exist',
      );
    });
  });
});
