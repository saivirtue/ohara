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

// ***********************************************************
// This example support/index.js is processed and
// loaded automatically before your test files.
//
// This is a great place to put global configuration and
// behavior that modifies Cypress.
//
// You can change the location of this file or turn off
// automatically serving support files with the
// 'supportFile' configuration option.
//
// You can read more here:
// https://on.cypress.io/configuration
// ***********************************************************

// eslint complain we use an undef object which is cypress pre-defined object "chai"
/* eslint-disable no-undef */

import '@cypress/code-coverage/support';

import './defaultCommands';
import './e2eCommands';
import './retryOnFail';
import { deleteAllServices } from '../utils';

after(async () => {
  // after each spec (file) tests finished, we make sure all service are stopped and deleted directly
  // even some tests of spec were failed, this command still run instantly.
  // see https://github.com/cypress-io/cypress/issues/203#issuecomment-251009808
  await deleteAllServices();
});

const customAssertions = (chai, utils) => {
  const customMethodA = (_super) => {
    return function a() {
      utils.flag(this, 'message', '[Type Assert]');
      _super.apply(this, arguments);
    };
  };
  const customChainingA = () => {
    return function chainA() {
      utils.flag(this, 'object');
    };
  };

  chai.Assertion.overwriteChainableMethod(
    'a',
    (_super) => customMethodA(_super),
    () => customChainingA(),
  );
  chai.Assertion.overwriteChainableMethod(
    'an',
    (_super) => customMethodA(_super),
    () => customChainingA(),
  );
};

chai.use(customAssertions);
