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

import { useMemo } from 'react';
import moment from 'moment';
import { get, map, find, isEmpty } from 'lodash';
import * as context from 'context';
import * as hooks from 'hooks';
import { TAB } from 'context/devTool/const';

export const useCurrentLogs = () => {
  const { data, query } = hooks.useDevToolLog();
  const { hostName } = query;

  return useMemo(() => {
    return get(find(data, { name: hostName }), 'logs');
  }, [data, hostName]);
};

export const useCurrentHosts = () => {
  const { data } = hooks.useDevToolLog();
  return useMemo(() => {
    return map(data, 'name');
  }, [data]);
};

export const useStatusText = () => {
  const { tabName } = context.useDevTool();
  const { query: logQuery } = hooks.useDevToolLog();
  const { timeGroup, timeRange, startTime, endTime } = logQuery;
  const { data: messages } = hooks.useDevToolTopicData();
  const currentLog = useCurrentLogs();

  return useMemo(() => {
    if (tabName === TAB.topic) {
      if (isEmpty(messages)) {
        return 'No topic data';
      } else {
        return `${messages.length} rows per query`;
      }
    } else {
      if (isEmpty(currentLog)) {
        return 'No log data';
      } else {
        switch (timeGroup) {
          case 'latest':
            return `Latest ${timeRange} minutes`;
          case 'customize':
            return `Customize from ${moment(startTime).format(
              'YYYY/MM/DD hh:mm',
            )} to ${moment(endTime).format('YYYY/MM/DD hh:mm')}`;
          default:
            return 'Unexpected time format';
        }
      }
    }
  }, [currentLog, endTime, messages, startTime, tabName, timeGroup, timeRange]);
};
