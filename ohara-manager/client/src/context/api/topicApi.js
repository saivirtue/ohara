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

import { isEmpty, map } from 'lodash';
import * as inspectApi from 'api/inspectApi';
import * as objectApi from 'api/objectApi';
import * as topicApi from 'api/topicApi';
import { generateClusterResponse, validate } from './utils';

export const createApi = context => {
  const {
    brokerGroup,
    topicGroup,
    workspaceGroup,
    workspaceName,
    showMessage,
  } = context;
  if (!brokerGroup || !topicGroup || !workspaceGroup || !workspaceName) return;

  const group = topicGroup;
  const brokerClusterKey = { group: brokerGroup, name: workspaceName };
  const parentKey = { group: workspaceGroup, name: workspaceName };
  return {
    fetchAll: async () => {
      const params = { group };
      const res = await topicApi.getAll(params);
      if (!isEmpty(res.errors)) {
        throw new Error(res.title);
      }
      return await Promise.all(
        map(res.data, async topic => {
          const params = { name: topic.name, group };
          const stageRes = await objectApi.get(params);
          if (!isEmpty(stageRes.errors)) {
            throw new Error(stageRes.title);
          }
          const infoRes = await inspectApi.getBrokerInfo(brokerClusterKey);
          if (!isEmpty(infoRes.errors)) {
            throw new Error(infoRes.title);
          }
          return generateClusterResponse({
            values: topic,
            stageValues: stageRes.data,
            inspectInfo: infoRes.data.classInfos[0],
          });
        }),
      );
    },
    fetch: async name => {
      const params = { name, group };
      const res = await topicApi.get(params);
      if (!isEmpty(res.errors)) {
        throw new Error(res.title);
      }
      const stageRes = await objectApi.get(params);
      if (!isEmpty(stageRes.errors)) {
        throw new Error(stageRes.title);
      }
      const infoRes = await inspectApi.getBrokerInfo(brokerClusterKey);
      if (!isEmpty(infoRes.errors)) {
        throw new Error(infoRes.title);
      }
      return generateClusterResponse({
        values: res.data,
        stageValues: stageRes.data,
        inspectInfo: infoRes.data.classInfos[0],
      });
    },
    create: async values => {
      try {
        validate(values);
        const params = {
          ...values,
          group,
          brokerClusterKey,
          tags: { ...values.tags, parentKey },
        };
        const res = await topicApi.create(params);
        if (!isEmpty(res.errors)) {
          throw new Error(res.title);
        }
        const stageRes = await objectApi.create(params);
        if (!isEmpty(stageRes.errors)) {
          throw new Error(stageRes.title);
        }
        const infoRes = await inspectApi.getBrokerInfo(brokerClusterKey);
        if (!isEmpty(infoRes.errors)) {
          throw new Error(infoRes.title);
        }
        const data = generateClusterResponse({
          values: res.data,
          stageValues: stageRes.data,
          inspectInfo: infoRes.data.classInfos[0],
        });
        showMessage(res.title);
        return data;
      } catch (e) {
        showMessage(e.message);
        throw e;
      }
    },
    update: async values => {
      try {
        validate(values);
        const params = { ...values, group };
        const res = await topicApi.update(params);
        if (!isEmpty(res.errors)) {
          throw new Error(res.title);
        }
        const data = generateClusterResponse({ values: res.data });
        showMessage(res.title);
        return data;
      } catch (e) {
        showMessage(e.message);
        throw e;
      }
    },
    stage: async values => {
      try {
        validate(values);
        const params = { ...values, group };
        const stageRes = await objectApi.update(params);
        if (!isEmpty(stageRes.errors)) {
          throw new Error(`Save topic ${values.name} failed.`);
        }
        const data = generateClusterResponse({ stageValues: stageRes.data });
        showMessage(`Save topic ${values.name} successful.`);
        return data;
      } catch (e) {
        showMessage(e.message);
        throw e;
      }
    },
    delete: async name => {
      try {
        const params = { name, group };
        const res = await topicApi.remove(params);
        if (!isEmpty(res.errors)) {
          throw new Error(res.title);
        }
        const stageRes = await objectApi.remove(params);
        if (!isEmpty(stageRes.errors)) {
          throw new Error(res.title);
        }
        showMessage(res.title);
        return params;
      } catch (e) {
        showMessage(e.message);
        throw e;
      }
    },
    start: async name => {
      try {
        const params = { name, group };
        const res = await topicApi.start(params);
        if (!isEmpty(res.errors)) {
          throw new Error(res.title);
        }
        const data = generateClusterResponse({ values: res.data });
        showMessage(res.title);
        return data;
      } catch (e) {
        showMessage(e.message);
        throw e;
      }
    },
    stop: async name => {
      try {
        const params = { name, group };
        const res = await topicApi.stop(params);
        if (!isEmpty(res.errors)) {
          throw new Error(res.title);
        }
        const data = generateClusterResponse({ values: res.data });
        showMessage(res.title);
        return data;
      } catch (e) {
        showMessage(e.message);
        throw e;
      }
    },
    fetchData: async values => {
      try {
        validate(values);
        const params = {
          ...values,
          group,
        };
        const res = await inspectApi.getTopicData(params);
        if (!isEmpty(res.errors)) {
          throw new Error(res.title);
        }
        const noTagsData = res.data.messages.map(message => {
          // we don't need the "tags" field in the topic data
          if (message.value) delete message.value.tags;
          return message;
        });
        const data = generateClusterResponse({ values: noTagsData });
        showMessage(res.title);
        return data;
      } catch (e) {
        showMessage(e.message);
        throw e;
      }
    },
  };
};
