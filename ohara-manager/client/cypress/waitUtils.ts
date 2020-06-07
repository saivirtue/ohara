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
import { isEmpty, isUndefined } from 'lodash';

import { API } from '../src/api/utils/apiUtils';
import * as commonUtils from '../src/utils/common';
import {
  SERVICE_STATE,
  ClusterResponse,
} from '../src/api/apiInterface/clusterInterface';
import { InspectServiceResponse } from '../src/api/apiInterface/inspectInterface';
import { TopicResponse } from '../src/api/apiInterface/topicInterface';
import {
  ObjectKey,
  BasicResponse,
} from '../src/api/apiInterface/basicInterface';

export const waitForRunning = (res: ClusterResponse) =>
  res.data.state === SERVICE_STATE.RUNNING;

export const waitForStopped = (res: ClusterResponse) =>
  isUndefined(res.data.state);

export const waitForClassInfosReady = (res: InspectServiceResponse) =>
  !isEmpty(res.data.classInfos);

export const waitForTopicReady = (res: TopicResponse) =>
  res.data.state === SERVICE_STATE.RUNNING;

export const wait = async <T extends BasicResponse>({
  api,
  objectKey,
  checkFn,
  checkParam,
  maxRetry = 10,
  sleep = 2000,
}: {
  api: API;
  objectKey: ObjectKey;
  checkFn: (res: T, params?: object) => boolean;
  checkParam?: object;
  maxRetry?: number;
  sleep?: number;
}): Promise<T> => {
  let retryCount = 0;
  while (retryCount < maxRetry) {
    try {
      const res = await api.get<T>({
        name: objectKey.name,
        queryParams: { group: objectKey.group },
      });
      if (checkFn(res, checkParam)) {
        return res;
      }
    } catch (error) {
      //Something went wrong, but we still retry until exceed maxRetry
      // eslint-disable-next-line no-console
      console.log('retry API failed but we will continue to execute: ', error);
    }

    await commonUtils.sleep(sleep);
    retryCount++;
  }
  const errorRes: BasicResponse = {
    data: {
      error: {
        code: 'N/A',
        message: 'exceed max retry',
        stack: '',
      },
    },
    status: -1,
    title: 'WaitApi Failed.',
  };
  throw errorRes;
};
