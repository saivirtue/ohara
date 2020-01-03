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

import React, { useState, useCallback, useEffect } from 'react';
import PropTypes from 'prop-types';
import { isEmpty } from 'lodash';
import { useLocation, useParams } from 'react-router-dom';
import { CellMeasurerCache } from 'react-virtualized/dist/commonjs/CellMeasurer';
import { WindowScroller } from 'react-virtualized/dist/commonjs/WindowScroller';

import * as context from 'context';
import DataTable from './DataTable';
import { tabName } from '../DevToolDialog';
import { useSnackbar } from 'context';
import { usePrevious } from 'utils/hooks';

// the react-virtualized <List> cached row style
const cache = new CellMeasurerCache({
  defaultHeight: 20,
  fixedWidth: true,
});

const DataWindow = () => {
  const location = useLocation();
  const { workspaceName, pipelineName } = useParams();
  const { setWorkspaceName, setPipelineName } = context.useApp();
  const { fetchTopicData } = context.useTopicActions();
  const {
    fetchConfiguratorLog,
    fetchZookeeperLog,
    fetchBrokerLog,
    fetchWorkerLog,
    fetchStreamLog,
  } = context.useLogActions();

  const prevWorkspaceName = usePrevious(workspaceName);
  const prevPipelineName = usePrevious(pipelineName);

  const searchParams = new URLSearchParams(location.search);
  const [isLoadingData, setIsLoadingData] = useState(false);
  const [topicResult, setTopicResult] = useState([]);
  const [logData, setLogData] = useState([]);
  const showMessage = useSnackbar();

  const type = searchParams.get('type') || '';

  // topics tab query parameter
  const topicName = searchParams.get('topic') || '';
  const topicLimit = searchParams.get('limit') || 10;
  const topicTimeout = searchParams.get('timeout') || 5000;
  // logs tab query parameter
  const service = searchParams.get('service') || '';
  const hostname = searchParams.get('hostname') || '';
  const timeSeconds = searchParams.get('timeSeconds') || '';
  const stream = searchParams.get('stream') || '';

  React.useEffect(() => {
    if (workspaceName && workspaceName !== prevWorkspaceName) {
      setWorkspaceName(workspaceName);
    }
  }, [prevWorkspaceName, setWorkspaceName, workspaceName]);

  React.useEffect(() => {
    if (pipelineName && pipelineName !== prevPipelineName) {
      setPipelineName(pipelineName);
    }
  }, [pipelineName, prevPipelineName, setPipelineName]);

  const fetchTopicDataCallback = useCallback(async () => {
    if (type !== tabName.topic) return;
    setIsLoadingData(true);
    const response = await fetchTopicData({
      name: topicName,
      limit: topicLimit,
      timeout: topicTimeout,
    });

    setTopicResult(response.data);
    setIsLoadingData(false);
  }, [type, topicName, topicLimit, topicTimeout, fetchTopicData]);

  useEffect(() => {
    if (!topicName) return;
    fetchTopicDataCallback();
  }, [topicName, fetchTopicDataCallback]);

  useEffect(() => {
    if (isEmpty(service)) return;

    const fetchLogs = async () => {
      let response;
      setIsLoadingData(true);
      switch (service) {
        case 'configurator':
          response = await fetchConfiguratorLog({
            sinceSeconds: timeSeconds,
          });
          break;
        case 'zookeeper':
          response = await fetchZookeeperLog({
            sinceSeconds: timeSeconds,
          });
          break;
        case 'broker':
          response = await fetchBrokerLog({
            sinceSeconds: timeSeconds,
          });
          break;
        case 'worker':
          response = await fetchWorkerLog({
            sinceSeconds: timeSeconds,
          });
          break;
        case 'stream':
          if (!isEmpty(stream)) {
            response = await fetchStreamLog({
              sinceSeconds: timeSeconds,
            });
          }
          break;
        default:
      }

      setIsLoadingData(false);

      if (response && !response.errors) {
        const result = response.data.logs
          // the hostname log should be unique, it is OK to "filter" the result
          .filter(log => log.hostname === hostname)
          .map(log => log.value.split('\n'));

        if (!isEmpty(result)) setLogData(result[0]);
        return;
      }
    };

    fetchLogs();
  }, [
    hostname,
    service,
    showMessage,
    stream,
    timeSeconds,
    fetchConfiguratorLog,
    fetchZookeeperLog,
    fetchBrokerLog,
    fetchWorkerLog,
    fetchStreamLog,
  ]);

  // we don't generate the data view if no query parameters existed
  if (!workspaceName || !pipelineName || !location.search) return null;

  switch (type) {
    case tabName.topic:
      return (
        <DataTable
          data={{
            topicData: topicResult,
            isLoading: isLoadingData,
          }}
          type={tabName.topic}
        />
      );
    case tabName.log:
      return (
        <WindowScroller>
          {({ height, isScrolling, onChildScroll, scrollTop }) => (
            <DataTable
              data={{
                hostLog: logData,
                isLoading: isLoadingData,
              }}
              type={tabName.log}
              cache={cache}
              windowOpts={{
                windowHeight: height,
                windowIsScrolling: isScrolling,
                windowOnScroll: onChildScroll,
                windowScrollTop: scrollTop,
              }}
            />
          )}
        </WindowScroller>
      );

    default:
      return null;
  }
};

DataWindow.propTypes = {
  location: PropTypes.shape({
    search: PropTypes.string,
  }).isRequired,
};

export default DataWindow;
