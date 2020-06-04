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

import { MODE } from '../../src/const';
import * as inspectApi from '../../src/api/inspectApi';
import { deleteAllServices } from '../utils';
import * as generate from '../../src/utils/generate';

let mode: MODE;
const nodeHost = Cypress.env('nodeHost');
const nodePort = Cypress.env('nodePort');
const nodeUser = Cypress.env('nodeUser');
const nodePass = Cypress.env('nodePass');

describe('NodeDialog of AppBar', () => {
  beforeEach(async () => {
    await deleteAllServices();
    const res = await inspectApi.getConfiguratorInfo();
    mode = res.data.mode;
  });

  it('check node list initially', () => {
    cy.visit('/');
    cy.findByTestId('close-intro-button').click();
    cy.findByTitle('Node list')
      .should('exist')
      .click();

    if (mode === MODE.DOCKER) {
      // empty node list in DOCKER mode
      cy.findByText(nodeHost).should('not.exist');
    } else if (mode === MODE.K8S) {
      // K8S will show the available node for us
      cy.findByText(nodeHost).should('exist');
    }
  });

  it('add a random node should be worked', () => {
    if (mode === MODE.K8S) {
      cy.log('Add node not support in K8S mode');
      return;
    }

    cy.visit('/');
    cy.findByTestId('close-intro-button').click();
    cy.findByTitle('Node list')
      .should('exist')
      .click();

    const hostname = generate.serviceName();
    cy.findByTitle('Create Node').click();
    cy.get('input[name=hostname]').type(hostname);
    cy.get('input[name=port]').type(generate.port().toString());
    cy.get('input[name=user]').type(generate.userName());
    cy.get('input[name=password]').type(generate.password());
    cy.findByText(/^create$/i).click();

    cy.findByTestId(`view-node-${hostname}`).click();

    // random added host should be dead
    cy.findAllByText(/^state$/i)
      .filter(':visible')
      .siblings('td')
      .contains('Unavailable')
      .should('exist');

    // press "ESC" back to node list
    cy.get('body:visible').trigger('keydown', { keyCode: 27, which: 27 });

    // delete the fake node we just added
    cy.findByTestId(`delete-node-${hostname}`).click();
    // confirm dialog
    cy.findByTestId('confirm-button-DELETE').click();

    // will auto back to node list, and the node list should be empty
    cy.findByText(hostname).should('not.exist');
  });

  it('filter nodes should be work', () => {
    cy.visit('/');
    cy.findByTestId('close-intro-button').click();
    cy.findByTitle('Node list')
      .should('exist')
      .click();

    if (mode === MODE.K8S) {
      cy.findAllByPlaceholderText('Search')
        .filter(':visible')
        .type(nodeHost);
      cy.findByText(nodeHost).should('exist');

      cy.findAllByPlaceholderText('Search')
        .filter(':visible')
        .clear()
        .type('fake');
      cy.findByText(nodeHost).should('not.exist');
      return;
    }

    const hostname1 = generate.serviceName();
    cy.findByTitle('Create Node').click();
    cy.get('input[name=hostname]').type(hostname1);
    cy.get('input[name=port]').type(generate.port().toString());
    cy.get('input[name=user]').type(generate.userName());
    cy.get('input[name=password]').type(generate.password());
    cy.findByText(/^create$/i).click();

    cy.findByText(hostname1).should('exist');

    const hostname2 = generate.serviceName();
    cy.findByTitle('Create Node').click();
    cy.get('input[name=hostname]').type(hostname2);
    cy.get('input[name=port]').type(generate.port().toString());
    cy.get('input[name=user]').type(generate.userName());
    cy.get('input[name=password]').type(generate.password());
    cy.findByText(/^create$/i).click();

    cy.findByText(hostname2).should('exist');

    cy.findByText(hostname1).should('exist');
    cy.findByText(hostname2).should('exist');

    cy.findAllByPlaceholderText('Search')
      .filter(':visible')
      .type(hostname2);

    cy.findByText(hostname1).should('not.exist');
    cy.findByText(hostname2).should('exist');

    cy.findAllByPlaceholderText('Search')
      .filter(':visible')
      .clear()
      .type('fake');

    cy.findByText(hostname1).should('not.exist');
    cy.findByText(hostname2).should('not.exist');
  });

  it('add a actual node and create service should be worked', () => {
    cy.visit('/');
    cy.findByTestId('close-intro-button').click();
    cy.findByTitle('Node list')
      .should('exist')
      .click();

    if (mode === MODE.DOCKER) {
      // We only need to add node if was DOCKER mode
      cy.findByTitle('Create Node').click();
      cy.get('input[name=hostname]').type(nodeHost);
      cy.get('input[name=port]').type(nodePort);
      cy.get('input[name=user]').type(nodeUser);
      cy.get('input[name=password]').type(nodePass);
      cy.findByText(/^create$/i).click();
    }

    cy.findByTestId(`view-node-${nodeHost}`).click();

    // actual host should be alive
    cy.findAllByText(/^state$/i)
      .siblings('td')
      .contains('Available')
      .should('exist');

    // press "ESC" back to node list
    cy.get('body:visible').trigger('keydown', { keyCode: 27, which: 27 });
    // press "ESC" again back to home page
    cy.findAllByText(/^all nodes$/i)
      .first()
      .trigger('keydown', { keyCode: 27, which: 27 });

    cy.createWorkspace({ workspaceName: generate.serviceName() });
    // wait for inspect worker
    cy.wait(15000);

    cy.findByTitle('Node list')
      .should('exist')
      .click();

    cy.findByTestId(`view-node-${nodeHost}`).click();

    cy.findByText(/^zookeeper$/i)
      .should('exist')
      .siblings('td')
      .contains('Running')
      .should('exist');
    cy.findByText(/^broker$/i)
      .should('exist')
      .siblings('td')
      .contains('Running')
      .should('exist');
    cy.findByText(/^connect-worker$/i)
      .should('exist')
      .siblings('td')
      .contains('Running')
      .should('exist');
  });
});

describe('NodeDialog of workspaceQuick', () => {
  beforeEach(async () => {
    await deleteAllServices();
    const res = await inspectApi.getConfiguratorInfo();
    mode = res.data.mode;
  });

  it('nodes should be able to selected and filtered', () => {
    if (mode === MODE.K8S) {
      cy.log('Add node not support in K8S mode');
      return;
    }
    cy.visit('/');
    cy.findByTestId('close-intro-button').click();
    cy.findByTitle('Node list')
      .should('exist')
      .click();

    const hostname1 = generate.serviceName();
    cy.findByTitle('Create Node').click();
    cy.get('input[name=hostname]').type(hostname1);
    cy.get('input[name=port]').type(generate.port().toString());
    cy.get('input[name=user]').type(generate.userName());
    cy.get('input[name=password]').type(generate.password());
    cy.findByText(/^create$/i).click();

    cy.findByText(hostname1).should('exist');

    const hostname2 = generate.serviceName();
    cy.findByTitle('Create Node').click();
    cy.get('input[name=hostname]').type(hostname2);
    cy.get('input[name=port]').type(generate.port().toString());
    cy.get('input[name=user]').type(generate.userName());
    cy.get('input[name=password]').type(generate.password());
    cy.findByText(/^create$/i).click();

    cy.findByText(hostname2).should('exist');

    const hostname3 = `${hostname1}${generate.serviceName()}`;
    cy.findByTitle('Create Node').click();
    cy.get('input[name=hostname]').type(hostname3);
    cy.get('input[name=port]').type(generate.port().toString());
    cy.get('input[name=user]').type(generate.userName());
    cy.get('input[name=password]').type(generate.password());
    cy.findByText(/^create$/i).click();

    cy.findByText(hostname3).should('exist');

    cy.visit('/');
    cy.findByText(/^quick create$/i)
      .should('exist')
      .click();

    // Step1: workspace name (using default)
    cy.findAllByText(/^next$/i)
      .filter(':visible')
      .click();

    // Since Unavailable node could not be selected
    // We check the existence only
    cy.findByText('Click here to select nodes').click();
    cy.findByText(hostname1).should('exist');
    cy.findByText(hostname2).should('exist');
    cy.findByText(hostname3).should('exist');

    // filter by hostname
    cy.findAllByPlaceholderText('Search')
      .filter(':visible')
      .type(hostname2);
    cy.findByText(hostname1).should('not.exist');
    cy.findByText(hostname3).should('not.exist');
  });
});
