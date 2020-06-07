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
import { ObjectKey, BasicResponse } from './apiInterface/basicInterface';
import {
  ObjectResponse,
  ObjectResponseList,
} from './apiInterface/objectInterface';
import { ServiceBody } from './apiInterface/clusterInterface';

const objectApi = new API(RESOURCE.OBJECT);

export const create = (params: ServiceBody) => {
  return objectApi.post<ObjectResponse>({ name: params.name, body: params });
};

export const update = (params: { [k: string]: any }) => {
  return objectApi.put<ObjectResponse>({
    name: params.name,
    group: params.group,
    body: omit(params, ['name', 'group']),
  });
};

export const remove = (objectKey: ObjectKey) => {
  return objectApi.delete<BasicResponse>(objectKey);
};

export const get = (objectKey: ObjectKey) => {
  return objectApi.get<ObjectResponse>({
    name: objectKey.name,
    queryParams: { group: objectKey.group },
  });
};

export const getAll = (queryParams?: object) => {
  return objectApi.get<ObjectResponseList>({ queryParams });
};
