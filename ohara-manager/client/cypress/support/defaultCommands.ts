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

// indicate this file is a module
export {};

const SINGLE_COMMAND_TIMEOUT = 5000;

Cypress.Commands.overwrite('get', (originFn, subject, options) => {
  // we only wait a few seconds for simple command instead of using defaultCommandTimeout
  const customOptions = {
    ...options,
    timeout: SINGLE_COMMAND_TIMEOUT,
  };
  return originFn(subject, customOptions);
});

Cypress.Commands.overwrite('click', (originFn, subject, options) => {
  // we only wait a few seconds for simple command instead of using defaultCommandTimeout
  const customOptions = {
    ...options,
    timeout: SINGLE_COMMAND_TIMEOUT,
  };
  return originFn(subject, customOptions);
});
