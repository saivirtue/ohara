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
import { deleteAllServices } from '../utils';

const prefix = Cypress.env('servicePrefix');

describe('DeleteWorkspace', () => {
  beforeEach(async () => await deleteAllServices());

  it('delete a workspace normally should be worked', () => {
    // create workspace
    cy.createWorkspace({});

    // click the settings dialog
    cy.findByText('workspace1').click();
    cy.contains('li', 'Settings').click();

    // click the delete workspace button
    cy.findByText('Delete this workspace').click();
    cy.findByPlaceholderText('workspace1').type('workspace1');

    cy.findByText('Delete').click();

    cy.findAllByText('workspace1').should('not.exist');
    cy.location().should((location) => {
      expect(location.pathname).to.be.eq('/');
    });
  });

  it('unexpected stop a workspace deletion should be deleted successfully after retry', () => {
    // create workspace
    cy.createWorkspace({});

    // click the settings dialog
    cy.findByText('workspace1').click();
    cy.contains('li', 'Settings').click();

    // click the delete workspace button
    cy.findByText('Delete this workspace').click();
    cy.findByPlaceholderText('workspace1').type('workspace1');

    cy.findByText('Delete').click();

    // wait a few milliseconds
    cy.wait(500);

    // simulate an incomplete deletion
    cy.reload();

    // click the delete workspace button again
    cy.findByText('workspace1').click();
    cy.contains('li', 'Settings').click();

    cy.findByText('Delete this workspace').click();
    cy.findByPlaceholderText('workspace1').type('workspace1');

    cy.findByText('Delete').click();

    cy.findAllByText('workspace1').should('not.exist');
    cy.location().should((location) => {
      expect(location.pathname).to.be.eq('/');
    });
  });

  it('delete a workspace of workspace list normally should be worked and switch to head workspace', () => {
    // create workspace with name
    const workspaceName = generate.serviceName({ prefix });
    cy.createWorkspace({ workspaceName });

    // create another workspace with default name
    cy.createWorkspace({});

    // click the settings dialog
    cy.findByText('workspace1').click();
    cy.contains('li', 'Settings').click();

    // click the delete workspace button
    cy.findByText('Delete this workspace').click();
    cy.findByPlaceholderText('workspace1').type('workspace1');

    cy.findByText('Delete').click();

    cy.findAllByText('workspace1').should('not.exist');
    cy.location().should((location) => {
      expect(location.pathname).to.be.eq(`/${workspaceName}`);
    });
  });
});
