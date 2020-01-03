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

import { map, reject } from 'lodash';

import * as routines from './topicRoutines';
import { isKeyEqual, sortByName } from 'utils/object';

const initialState = {
  isFetching: false,
  data: [],
  lastUpdated: null,
  error: null,
};

const reducer = (state, action) => {
  switch (action.type) {
    case routines.initializeRoutine.TRIGGER:
      return initialState;
    case routines.fetchTopicsRoutine.REQUEST:
    case routines.createTopicRoutine.REQUEST:
    case routines.updateTopicRoutine.REQUEST:
    case routines.deleteTopicRoutine.REQUEST:
      return {
        ...state,
        isFetching: true,
        error: null,
      };
    case routines.fetchTopicDataRoutine.REQUEST:
      return {
        ...state,
        isFetching: true,
        error: null,
      };
    case routines.fetchTopicsRoutine.SUCCESS:
      return {
        ...state,
        isFetching: false,
        data: sortByName(action.payload),
        lastUpdated: new Date(),
      };
    case routines.createTopicRoutine.SUCCESS:
      return {
        ...state,
        isFetching: false,
        data: sortByName([...state.data, action.payload]),
        lastUpdated: new Date(),
      };
    case routines.updateTopicRoutine.SUCCESS:
      return {
        ...state,
        isFetching: false,
        data: map(state.data, topic =>
          isKeyEqual(topic, action.payload)
            ? { ...topic, ...action.payload }
            : topic,
        ),
        lastUpdated: new Date(),
      };
    case routines.fetchTopicDataRoutine.SUCCESS:
      return {
        ...state,
        isFetching: false,
        lastUpdated: new Date(),
      };
    case routines.deleteTopicRoutine.SUCCESS:
      return {
        ...state,
        isFetching: false,
        data: reject(state.data, topic => isKeyEqual(topic, action.payload)),
        lastUpdated: new Date(),
      };
    case routines.fetchTopicsRoutine.FAILURE:
    case routines.createTopicRoutine.FAILURE:
    case routines.updateTopicRoutine.FAILURE:
    case routines.deleteTopicRoutine.FAILURE:
    case routines.fetchTopicDataRoutine.FAILURE:
      return {
        ...state,
        isFetching: false,
        error: action.payload,
      };
    default:
      return state;
  }
};

export { reducer, initialState };
