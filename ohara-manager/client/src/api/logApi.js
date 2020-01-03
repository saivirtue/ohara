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

import * as log from './body/logBody';
import { getKey, responseUtil, axiosInstance } from './utils/apiUtils';
import * as URL from './utils/url';

const url = URL.LOG_URL;

export const logServices = {
  configurator: 'configurator',
  zookeeper: 'zookeepers',
  broker: 'brokers',
  worker: 'workers',
  stream: 'streams',
};

export const getConfiguratorLog = async (params = {}) => {
  const res = await axiosInstance.get(
    `${url}/${logServices.configurator}${URL.toQueryParameters(params)}`,
  );
  const result = responseUtil(res, log);
  result.title =
    `Get ${logServices.configurator} log ${getKey(params)} ` +
    (result.errors ? 'failed.' : 'successful.');
  return result;
};

export const getZookeeperLog = async params => {
  const { name } = params;
  const res = await axiosInstance.get(
    `${url}/${logServices.zookeeper}/${name}${URL.toQueryParameters(params)}`,
  );
  const result = responseUtil(res, log);
  result.title =
    `Get ${logServices.zookeeper} log ${getKey(params)} ` +
    (result.errors ? 'failed.' : 'successful.');
  return result;
};

export const getBrokerLog = async params => {
  const { name } = params;
  const res = await axiosInstance.get(
    `${url}/${logServices.broker}/${name}${URL.toQueryParameters(params)}`,
  );
  const result = responseUtil(res, log);
  result.title =
    `Get ${logServices.broker} log ${getKey(params)} ` +
    (result.errors ? 'failed.' : 'successful.');
  return result;
};

export const getWorkerLog = async params => {
  const { name } = params;
  const res = await axiosInstance.get(
    `${url}/${logServices.worker}/${name}${URL.toQueryParameters(params)}`,
  );
  const result = responseUtil(res, log);
  result.title =
    `Get ${logServices.worker} log ${getKey(params)} ` +
    (result.errors ? 'failed.' : 'successful.');
  return result;
};

export const getStreamLog = async params => {
  const { name } = params;
  const res = await axiosInstance.get(
    `${url}/${logServices.stream}/${name}${URL.toQueryParameters(params)}`,
  );
  const result = responseUtil(res, log);
  result.title =
    `Get ${logServices.stream} log ${getKey(params)} ` +
    (result.errors ? 'failed.' : 'successful.');
  return result;
};
