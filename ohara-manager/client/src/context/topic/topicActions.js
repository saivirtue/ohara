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

import * as routines from './topicRoutines';
import * as action from 'utils/action';

export const createActions = context => {
  const { state, dispatch, topicApi } = context;
  return {
    fetchTopics: async () => {
      const routine = routines.fetchTopicsRoutine;
      if (state.isFetching || state.lastUpdated || state.error) return;
      try {
        dispatch(routine.request());
        const data = await topicApi.fetchAll();
        dispatch(routine.success(data));
        return action.success(data);
      } catch (e) {
        dispatch(routine.failure(e.message));
        return action.failure(e.message);
      }
    },
    createTopic: async values => {
      const routine = routines.createTopicRoutine;
      if (state.isFetching) return;
      try {
        dispatch(routine.request());
        await topicApi.create(values);
        await topicApi.start(values.name);
        const data = await topicApi.fetch(values.name);
        dispatch(routine.success(data));
        return action.success(data);
      } catch (e) {
        dispatch(routine.failure(e.message));
        return action.failure(e.message);
      }
    },
    updateTopic: async values => {
      const routine = routines.updateTopicRoutine;
      if (state.isFetching) return;
      try {
        dispatch(routine.request());
        const data = await topicApi.update(values);
        dispatch(routine.success(data));
        return action.success(data);
      } catch (e) {
        dispatch(routine.failure(e.message));
        return action.failure(e.message);
      }
    },
    deleteTopic: async name => {
      const routine = routines.deleteTopicRoutine;
      if (state.isFetching) return;
      try {
        dispatch(routine.request());
        await topicApi.stop(name);
        const data = await topicApi.delete(name);
        dispatch(routine.success(data));
        return action.success(data);
      } catch (e) {
        dispatch(routine.failure(e.message));
        return action.failure(e.message);
      }
    },
    startTopic: async name => {
      const routine = routines.startTopicRoutine;
      if (state.isFetching) return;
      try {
        dispatch(routine.request());
        const data = await topicApi.start(name);
        dispatch(routine.success(data));
        return action.success(data);
      } catch (e) {
        dispatch(routine.failure(e.message));
        return action.failure(e.message);
      }
    },
    stopTopic: async name => {
      const routine = routines.stopTopicRoutine;
      if (state.isFetching) return;
      try {
        dispatch(routine.request());
        const data = await topicApi.stop(name);
        dispatch(routine.success(data));
        return action.success(data);
      } catch (e) {
        dispatch(routine.failure(e.message));
        return action.failure(e.message);
      }
    },
    fetchTopicData: async values => {
      const routine = routines.fetchTopicDataRoutine;
      if (state.isFetching) return;
      try {
        dispatch(routine.request());
        const data = await topicApi.fetchData(values);
        dispatch(routine.success(data));
        return action.success(data);
      } catch (e) {
        dispatch(routine.failure(e.message));
        return action.failure(e.message);
      }
    },
  };
};
