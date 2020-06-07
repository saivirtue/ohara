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

// It's uppercase in AppBar
const workspaceNameInAppBar = Cypress.env('servicePrefix')
  .substring(0, 2)
  .toUpperCase();

describe('Root route', () => {
  before(async () => await deleteAllServices());

  it('should display root route', () => {
    cy.visit('/')
      .location()
      .should((location) => {
        expect(location.pathname).to.be.eq('/');
      });
    cy.findByText(/^quick create$/i)
      .should('exist')
      .end();
  });
});

describe('Redirect route', () => {
  before(async () => await deleteAllServices());

  it('should redirect to default workspace and pipeline', () => {
    const workspaceName = generate.serviceName({
      prefix: Cypress.env('servicePrefix'),
    });
    cy.createWorkspace({ workspaceName });

    cy.visit('/').findAllByText(workspaceNameInAppBar).should('exist');

    cy.location().should((location) => {
      expect(location.pathname).to.be.eq(`/${workspaceName}`);
    });

    // not exist workspace will redirect to default workspace
    cy.visit('/fakeworkspacehaha');

    cy.location().should((location) => {
      expect(location.pathname).to.be.eq(`/${workspaceName}`);
    });

    // Add new pipeline
    cy.findByText(/^pipelines$/i)
      .siblings('svg')
      .first()
      .click()
      .findByText(/^add a new pipeline$/i)
      .should('exist');

    cy.findByTestId('new-pipeline-dialog').find('input').type('pipeline1');

    cy.findByText(/^add$/i).click();

    cy.location().should((location) => {
      expect(location.pathname).to.be.eq(`/${workspaceName}/pipeline1`);
    });

    // not exist workspace will redirect to default workspace with pipeline
    cy.visit('/fakeworkspacehaha');

    cy.location().should((location) => {
      expect(location.pathname).to.be.eq(`/${workspaceName}/pipeline1`);
    });

    // not exist pipeline will redirect to default workspace
    cy.visit(`/${workspaceName}/foobar`);

    cy.location().should((location) => {
      expect(location.pathname).to.be.eq(`/${workspaceName}/pipeline1`);
    });

    // not exist workspace and pipeline will redirect to default workspace with pipeline
    cy.visit(`/fakeworkspacehaha/foobar`);

    cy.location().should((location) => {
      expect(location.pathname).to.be.eq(`/${workspaceName}/pipeline1`);
    });
  });
});

describe('Not implement page', () => {
  it('should display page not implement route', () => {
    cy.visit('/501-page-not-implemented').contains('501').should('exist');
  });
});

describe('Not found page', () => {
  it('should display page not found route', () => {
    cy.visit('/jladkf/safkj/ksjdl/jlkfsd/kjlfds')
      .contains('404')
      .should('exist');
  });
});
