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
import { ObjectKey } from './apiInterface/basicInterface';
import { LogResponse, LOG_SERVICES } from './apiInterface/logInterface';

const logApi = (service: LOG_SERVICES) => new API(`${RESOURCE.LOG}/${service}`);

const fetchServiceLog = (
  service: LOG_SERVICES,
  objectKey?: ObjectKey,
  queryParams?: object,
) => {
  return logApi(service).get<LogResponse>({
    name: objectKey?.name,
    queryParams: { group: objectKey?.group, ...queryParams },
  });
};

export const getConfiguratorLog = (queryParams?: object) => {
  return logApi(LOG_SERVICES.configurator).get<LogResponse>({ queryParams });
};

export const getZookeeperLog = (objectKey: ObjectKey, queryParams?: object) => {
  return fetchServiceLog(LOG_SERVICES.zookeeper, objectKey, queryParams);
};

export const getBrokerLog = (objectKey: ObjectKey, queryParams?: object) => {
  return fetchServiceLog(LOG_SERVICES.broker, objectKey, queryParams);
};

export const getWorkerLog = (objectKey: ObjectKey, queryParams?: object) => {
  return fetchServiceLog(LOG_SERVICES.worker, objectKey, queryParams);
};

export const getShabondiLog = (objectKey: ObjectKey, queryParams?: object) => {
  return fetchServiceLog(LOG_SERVICES.shabondi, objectKey, queryParams)
    .then((res) => {
      res.title = `Get ${RESOURCE.LOG}/${LOG_SERVICES.shabondi} "${objectKey.name}" info successfully.`;
      return res;
    })
    .catch((error: LogResponse) => {
      error.title = `Get ${RESOURCE.LOG}/${LOG_SERVICES.shabondi} "${objectKey.name}" info failed.`;
      throw error;
    });
};

export const getStreamLog = (objectKey: ObjectKey, queryParams?: object) => {
  return fetchServiceLog(LOG_SERVICES.stream, objectKey, queryParams)
    .then((res) => {
      res.title = `Get ${RESOURCE.LOG}/${LOG_SERVICES.stream} "${objectKey.name}" info successfully.`;
      return res;
    })
    .catch((error: LogResponse) => {
      error.title = `Get ${RESOURCE.LOG}/${LOG_SERVICES.stream} "${objectKey.name}" info failed.`;
      throw error;
    });
};
