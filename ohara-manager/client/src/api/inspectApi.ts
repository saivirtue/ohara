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
import { ObjectKey } from './apiInterface/basicInterface';
import * as inspect from './apiInterface/inspectInterface';
import { FileRequest, FileResponse } from './apiInterface/fileInterface';

const hiddenDefinitions = [
  'group',
  'zookeeperClusterKey',
  'workerClusterKey',
  'brokerClusterKey',
];

export enum INSPECT_KIND {
  configurator = 'configurator',
  zookeeper = 'zookeeper',
  broker = 'broker',
  worker = 'worker',
  stream = 'stream',
  shabondi = 'shabondi',
  manager = 'manager',
  rdb = 'rdb',
  topic = 'topic',
  file = 'files',
}

const inspectApi = (kind: INSPECT_KIND) =>
  new API(`${RESOURCE.INSPECT}/${kind}`);

const fetchServiceInfo = (kind: INSPECT_KIND, objectKey?: ObjectKey) => {
  return inspectApi(kind)
    .get<inspect.InspectServiceResponse>({
      name: objectKey?.name,
      queryParams: { group: objectKey?.group },
    })
    .then((res) => {
      res.title = `Inspect ${kind} ${objectKey?.name} info successfully .`;

      res.data.settingDefinitions.forEach((definition) => {
        // The value `.` doesn't work very well with final form
        // We replace all "." by "__" in key and group field
        definition.key = definition.key.replace(/\./g, '__');
        definition.group = definition.group.replace(/\./g, '__');

        // Hide the def which we don't want to show in UI
        if (hiddenDefinitions.includes(definition.key)) {
          definition.internal = true;
        }
      });
      res.data.classInfos.forEach((classInfo) => {
        classInfo.settingDefinitions.forEach((definition) => {
          // The value `.` doesn't work very well with final form
          // We replace all "." by "__" in key and group field
          definition.key = definition.key.replace(/\./g, '__');
          definition.group = definition.group.replace(/\./g, '__');

          // Hide the def which we don't want to show in UI
          if (hiddenDefinitions.includes(definition.key)) {
            definition.internal = true;
          }
        });
      });
      return res;
    })
    .catch((error: inspect.InspectServiceResponse) => {
      error.title = `Inspect ${kind} ${objectKey?.name} info failed.`;
      throw error;
    });
};

export const getConfiguratorInfo = () => {
  return inspectApi(INSPECT_KIND.configurator)
    .get<inspect.InspectConfiguratorResponse>()
    .then((res) => {
      res.title = `Inspect ${INSPECT_KIND.configurator} info successfully.`;

      return res;
    })
    .catch((error: inspect.InspectConfiguratorResponse) => {
      error.title = `Inspect ${INSPECT_KIND.configurator} info failed.`;
      throw error;
    });
};

// Note this API is a special route from manager, not configurator
// it depends on a gradle task :ohara-common:versionFile to generate version
// info when running in dev mode
export const getManagerInfo = () => {
  return inspectApi(INSPECT_KIND.manager)
    .get<inspect.InspectManagerResponse>()
    .then((res) => {
      res.title = `Inspect ${INSPECT_KIND.manager} info successfully.`;

      return res;
    })
    .catch((error: inspect.InspectConfiguratorResponse) => {
      error.title = `Inspect ${INSPECT_KIND.manager} info failed.`;
      throw error;
    });
};

export const getZookeeperInfo = (objectKey?: ObjectKey) => {
  return fetchServiceInfo(INSPECT_KIND.zookeeper, objectKey);
};

export const getBrokerInfo = (objectKey?: ObjectKey) => {
  return fetchServiceInfo(INSPECT_KIND.broker, objectKey);
};

export const getWorkerInfo = (objectKey?: ObjectKey) => {
  return fetchServiceInfo(INSPECT_KIND.worker, objectKey);
};

export const getStreamsInfo = (objectKey?: ObjectKey) => {
  return fetchServiceInfo(INSPECT_KIND.stream, objectKey);
};

export const getShabondiInfo = (objectKey?: ObjectKey) => {
  return fetchServiceInfo(INSPECT_KIND.shabondi, objectKey);
};

export const getFileInfoWithoutUpload = (params: FileRequest) => {
  return inspectApi(INSPECT_KIND.file)
    .upload<FileResponse>({ params })
    .then((res) => {
      res.title = `Inspect ${INSPECT_KIND.file} "${params.name}" info successfully.`;

      return res;
    })
    .catch((error: FileResponse) => {
      error.title = `Inspect ${INSPECT_KIND.file} "${params.name}" info failed.`;
      throw error;
    });
};

export const getTopicData = (params: inspect.InspectTopicRequest) => {
  return inspectApi(INSPECT_KIND.topic).query<inspect.InspectTopicResponse>({
    name: params.key.name,
    queryParams: { group: params.key.group, ...omit(params, ['key']) },
  });
};

export const getRdbData = (params: inspect.InspectRdbRequest) => {
  return inspectApi(INSPECT_KIND.rdb).query<inspect.InspectRdbResponse>({
    queryParams: params,
  });
};
