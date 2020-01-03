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

import * as routines from './logRoutines';
import * as action from 'utils/action';

export const createActions = context => {
  const { state, dispatch, logApi } = context;
  return {
    fetchConfiguratorLog: async values => {
      const routine = routines.fetchConfiguratorRoutine;
      if (state.isFetching || state.lastUpdated || state.error) return;
      try {
        dispatch(routine.request());
        const data = await logApi.fetchConfigurator(values);
        dispatch(routine.success(data));
        return action.success(data);
      } catch (e) {
        dispatch(routine.failure(e.message));
        return action.failure(e.message);
      }
    },
    fetchZookeeperLog: async values => {
      const routine = routines.fetchZookeeperRoutine;
      if (state.isFetching || state.lastUpdated || state.error) return;
      try {
        dispatch(routine.request());
        const data = await logApi.fetchZookeeper(values);
        dispatch(routine.success(data));
        return action.success(data);
      } catch (e) {
        dispatch(routine.failure(e.message));
        return action.failure(e.message);
      }
    },
    fetchBrokerLog: async values => {
      const routine = routines.fetchBrokerRoutine;
      if (state.isFetching || state.lastUpdated || state.error) return;
      try {
        dispatch(routine.request());
        const data = await logApi.fetchBroker(values);
        dispatch(routine.success(data));
        return action.success(data);
      } catch (e) {
        dispatch(routine.failure(e.message));
        return action.failure(e.message);
      }
    },
    fetchWorkerLog: async values => {
      const routine = routines.fetchWorkerRoutine;
      if (state.isFetching || state.lastUpdated || state.error) return;
      try {
        dispatch(routine.request());
        const data = await logApi.fetchWorker(values);
        dispatch(routine.success(data));
        return action.success(data);
      } catch (e) {
        dispatch(routine.failure(e.message));
        return action.failure(e.message);
      }
    },
    fetchStreamLog: async values => {
      const routine = routines.fetchStreamRoutine;
      if (state.isFetching || state.lastUpdated || state.error) return;
      try {
        dispatch(routine.request());
        const data = await logApi.fetchStream(values);
        dispatch(routine.success(data));
        return action.success(data);
      } catch (e) {
        dispatch(routine.failure(e.message));
        return action.failure(e.message);
      }
    },
  };
};
