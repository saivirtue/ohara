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

import { toNumber, get } from 'lodash';

import { handleError, axiosInstance } from './apiUtils';

export const fetchWorker = async name => {
  try {
    const res = await axiosInstance.get(`/api/workers/${name}`);
    const isSuccess = get(res, 'data.isSuccess', false);

    if (!isSuccess) {
      handleError(res);
    }

    return res;
  } catch (err) {
    handleError(err);
  }
};

export const fetchWorkers = async () => {
  try {
    const res = await axiosInstance.get(`/api/workers`);
    const isSuccess = get(res, 'data.isSuccess', false);

    if (!isSuccess) {
      handleError(res);
    }

    return res;
  } catch (err) {
    handleError(err);
  }
};

export const createWorker = async params => {
  try {
    const url = `/api/workers`;
    const jars = get(params, 'plugins', []).map(jar => {
      return {
        name: jar.name,
        group: jar.group,
      };
    });

    const data = {
      name: params.name,
      jmxPort: params.jmxPort,
      brokerClusterName: params.brokerClusterName,
      clientPort: toNumber(params.clientPort),
      nodeNames: params.nodeNames || [],
      jars: jars,
      groupId: params.groupId,
      configTopicName: params.configTopicName,
      offsetTopicName: params.offsetTopicName,
      statusTopicName: params.statusTopicName,
    };
    const config = {
      timeout: 3 * 60 * 1000, // set timeout to 3 minutes.
    };

    const res = await axiosInstance.post(url, data, config);
    const isSuccess = get(res, 'data.isSuccess', false);

    if (!isSuccess) {
      handleError(res);
    }

    return res;
  } catch (err) {
    handleError(err);
  }
};

export const addNodeToWorker = async params => {
  try {
    const { name, nodeName } = params;
    const url = `/api/workers/${name}/${nodeName}`;
    const res = await axiosInstance.put(url);
    const isSuccess = get(res, 'data.isSuccess', false);

    if (!isSuccess) {
      handleError(res);
    }

    return res;
  } catch (err) {
    handleError(err);
  }
};

export const startWorker = async name => {
  try {
    const url = `/api/workers/${name}/start`;

    const res = await axiosInstance.put(url);
    const isSuccess = get(res, 'data.isSuccess', false);

    if (!isSuccess) {
      handleError(res);
    }

    return res;
  } catch (err) {
    handleError(err);
  }
};

export const stopWorker = async name => {
  try {
    const url = `/api/workers/${name}/stop`;

    const res = await axiosInstance.put(url);
    const isSuccess = get(res, 'data.isSuccess', false);

    if (!isSuccess) {
      handleError(res);
    }

    return res;
  } catch (err) {
    handleError(err);
  }
};

export const deleteWorker = async name => {
  try {
    const url = `/api/workers/${name}`;
    const res = await axiosInstance.delete(url);
    const isSuccess = get(res, 'data.isSuccess', false);

    if (!isSuccess) {
      handleError(res);
    }

    return res;
  } catch (err) {
    handleError(err);
  }
};
