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
import { omit } from 'lodash';
import { RESOURCE, API } from '../api/utils/apiUtils';
import {
  NodeRequest,
  NodeResponse,
  NodeResponseList,
} from './apiInterface/nodeInterface';
import { BasicResponse } from './apiInterface/basicInterface';

const nodeApi = new API(RESOURCE.NODE);

export const create = (params: NodeRequest) => {
  return nodeApi.post<NodeResponse>({ name: params.hostname, body: params });
};

export const update = (params: NodeRequest) => {
  return nodeApi.put<NodeResponse>({
    name: params.hostname,
    body: omit(params, ['hostname']),
  });
};

export const remove = (hostname: string) => {
  return nodeApi.delete<BasicResponse>({ name: hostname });
};

export const get = (hostname: string) => {
  return nodeApi.get<NodeResponse>({ name: hostname });
};

export const getAll = (queryParams?: object) => {
  return nodeApi.get<NodeResponseList>({ queryParams });
};
