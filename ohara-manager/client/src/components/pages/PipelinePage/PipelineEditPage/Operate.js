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

import React from 'react';
import PropTypes from 'prop-types';
import Tooltip from '@material-ui/core/Tooltip';

import * as connectorApi from 'api/connectorApi';
import * as streamApi from 'api/streamApi';
import useSnackbar from 'components/context/Snackbar/useSnackbar';
import { Box } from 'components/common/Layout';
import { Heading3, OperateWrapper } from './styles';
import { getConnectors } from '../pipelineUtils';

const Operate = props => {
  const { fetchPipeline, updateHasRunningServices, pipeline } = props;
  const { showMessage } = useSnackbar();

  const {
    name: pipelineName,
    objects: pipelineConnectors,
    group: pipelineGroup,
    tags: { workerClusterName },
  } = pipeline;

  const streamGroup = workerClusterName;

  const makeRequest = (action, connectors) => {
    const { sources, sinks, streams } = getConnectors(connectors);
    const _connectors = sources.concat(sinks);

    let connectorPromises = [];
    let streamsPromises = [];

    if (action === 'start') {
      connectorPromises = _connectors.map(connector =>
        connectorApi.startConnector(pipelineGroup, connector),
      );

      streamsPromises = streams.map(stream =>
        streamApi.startStreamApp(streamGroup, stream),
      );
    } else {
      connectorPromises = _connectors.map(connector =>
        connectorApi.stopConnector(pipelineGroup, connector),
      );

      streamsPromises = streams.map(stream =>
        streamApi.stopStreamApp(streamGroup, stream),
      );
    }

    return Promise.all([...connectorPromises, ...streamsPromises]).then(
      result => result,
    );
  };

  const handleActionClick = async action => {
    let responses = null;

    if (action === 'start') {
      try {
        responses = await makeRequest(action, pipelineConnectors);
      } catch (error) {
        showMessage(error.message);
      }
    } else {
      try {
        responses = await makeRequest(action, pipelineConnectors);
      } catch (error) {
        showMessage(error.message);
      }
    }

    if (responses) {
      const hasError = responses.some(response => !response.data.isSuccess);

      if (!hasError) {
        showMessage(`Pipeline has been successfully ${action}!`);

        const hasRunningServices = action === 'start';
        updateHasRunningServices(hasRunningServices);
        await fetchPipeline(pipelineGroup, pipelineName);
      }
    }
  };

  return (
    <Box>
      <OperateWrapper>
        <div className="actions">
          <Heading3>Operate</Heading3>
          <div className="action-btns">
            <Tooltip title="Start pipeline" enterDelay={1000}>
              <button
                className="start-btn"
                onClick={() => handleActionClick('start')}
                data-testid="start-btn"
              >
                <i className="far fa-play-circle" />
              </button>
            </Tooltip>

            <Tooltip title="Stop pipeline" enterDelay={1000}>
              <button
                className="stop-btn"
                onClick={() => handleActionClick('stop')}
                data-testid="stop-btn"
              >
                <i className="far fa-stop-circle" />
              </button>
            </Tooltip>
          </div>
        </div>
        <span className="cluster-name">
          This pipeline is running on: {workerClusterName}
        </span>
      </OperateWrapper>
    </Box>
  );
};

Operate.propTypes = {
  fetchPipeline: PropTypes.func.isRequired,
  pipeline: PropTypes.shape({
    name: PropTypes.string.isRequired,
    objects: PropTypes.arrayOf(PropTypes.object),
    group: PropTypes.string.isRequired,
    tags: PropTypes.shape({
      workerClusterName: PropTypes.string.isRequired,
    }).isRequired,
  }).isRequired,
  updateHasRunningServices: PropTypes.func.isRequired,
};

export default Operate;
