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
import { RESOURCE, API } from '../api/utils/apiUtils';
import { ValidateResponse } from './apiInterface/validateInterface';
import { ServiceBody } from './apiInterface/clusterInterface';

// for validate api, we only support validating connector now
// the resource should be 'validate/connector'
const validateApi = new API(`${RESOURCE.VALIDATE}/connector`);

export const validateConnector = (params: ServiceBody) => {
  return validateApi
    .put<ValidateResponse>({ body: params })
    .then((res) => {
      res.title = `Validate connector "${params.name}" info successfully.`;
      return res;
    })
    .catch((error: ValidateResponse) => {
      error.title = `Validate connector "${params.name}" info failed.`;
      throw error;
    });
};
