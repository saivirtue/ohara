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

import { toString, reject, includes } from 'lodash';
import * as actions from 'store/actions';

const initialState = {
  isFetching: false,
  data: [],
  lastUpdated: null,
  error: null,
  settings: {
    data: {
      limit: 1000,
      unlimited: false,
    },
    isFetching: false,
    lastUpdated: null,
    error: null,
  },
  notifications: {
    data: {
      info: 0,
      error: 0,
    },
    isFetching: false,
    lastUpdated: null,
    error: null,
  },
};

export default function reducer(state = initialState, action) {
  switch (action.type) {
    case actions.fetchEventLogs.TRIGGER:
      return {
        ...state,
        data: [],
        isFetching: true,
      };
    case actions.fetchEventLogs.SUCCESS:
      return {
        ...state,
        isFetching: false,
        data: action.payload || [],
        lastUpdated: new Date(),
      };
    case actions.createEventLog.SUCCESS:
      return {
        ...state,
        isFetching: false,
        data: [...state.data, action.payload],
        lastUpdated: new Date(),
      };
    case actions.deleteEventLogs.SUCCESS:
      return {
        ...state,
        isFetching: false,
        data: reject(state.data, log => {
          return includes(action.payload, log.key);
        }),
        lastUpdated: new Date(),
      };
    case actions.clearEventLogs.SUCCESS:
      return {
        ...state,
        isFetching: false,
        data: [],
        lastUpdated: new Date(),
      };
    case actions.updateSettings.SUCCESS:
      return {
        ...state,
        settings: {
          ...state.settings,
          data: { ...state.settings.data, ...action.payload },
          isFetching: false,
          lastUpdated: new Date(),
        },
      };
    case actions.updateNotifications.SUCCESS:
      return {
        ...state,
        notifications: {
          ...state.notifications,
          data: { ...state.notifications.data, ...action.payload },
          isFetching: false,
          lastUpdated: new Date(),
        },
      };
    case actions.clearNotifications.SUCCESS:
      return {
        ...state,
        notifications: {
          ...state.notifications,
          data: { ...initialState.notifications.data },
          isFetching: false,
          lastUpdated: new Date(),
        },
      };
    case actions.fetchEventLogs.FAILURE:
    case actions.createEventLog.FAILURE:
    case actions.clearEventLogs.FAILURE:
      return {
        ...state,
        isFetching: false,
        error: toString(action.payload),
      };
    case actions.updateSettings.FAILURE:
      return {
        ...state,
        settings: {
          ...state.settings,
          isFetching: false,
          error: toString(action.payload),
        },
      };
    case actions.updateNotifications.FAILURE:
    case actions.clearNotifications.FAILURE:
      return {
        ...state,
        notifications: {
          ...state.notifications,
          isFetching: false,
          error: toString(action.payload),
        },
      };
    default:
      return state;
  }
}
