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

describe('workspaceQuick', () => {
  beforeEach(async () => await deleteAllServices());

  it('using quick mode to create workspace', () => {
    // create workspace
    cy.createWorkspace({});

    // create another workspace with name
    const workspaceName = generate.serviceName({ prefix });
    cy.createWorkspace({ workspaceName });

    // we have two workspace now
    cy.findByTitle('Workspace list')
      .children()
      .should('not.be.disabled')
      .click();
    cy.findByText(/^showing 2 workspaces$/i).should('exist');
  });
});
