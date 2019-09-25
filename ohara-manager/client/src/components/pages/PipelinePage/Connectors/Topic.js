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

import React, { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import { get, isEmpty } from 'lodash';

import * as pipelineApi from 'api/pipelineApi';
import * as MESSAGES from 'constants/messages';
import * as topicApi from 'api/topicApi';
import * as s from './styles';
import Controller from './Controller';
import useSnackbar from 'components/context/Snackbar/useSnackbar';
import { ListLoader } from 'components/common/Loader';
import { Box } from 'components/common/Layout';
import { FormGroup, Label, Input } from 'components/common/Form';
import { graph as graphPropType } from 'propTypes/pipeline';

const Topic = props => {
  const [topic, setTopic] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const { showMessage } = useSnackbar();

  const topicName = props.match.params.connectorName;
  const { workerClusterName } = props.pipeline.tags;
  const topicGroup = workerClusterName;

  useEffect(() => {
    const fetchTopic = async () => {
      try {
        const res = await topicApi.fetchTopic(topicGroup, topicName);
        const topic = get(res, 'data.result', null);
        if (topic) setTopic(topic);
      } catch (error) {
        showMessage(error.message);
      }
      setIsLoading(false);
    };

    fetchTopic();
  }, [showMessage, topicGroup, topicName]);

  const hasConnection = (flows, topicName) => {
    const hasToConnections = flows.some(flow => {
      if (flow.from.name === topicName) {
        return !isEmpty(flow.to);
      }

      return false;
    });

    const hasFromConnections = flows.some(flow => {
      return flow.to.some(t => t.name === topicName);
    });

    return hasFromConnections || hasToConnections;
  };

  const deleteTopic = async () => {
    const { history, pipeline, refreshGraph } = props;
    const { name, flows, group } = pipeline;

    if (hasConnection(flows, topicName)) {
      showMessage(MESSAGES.CANNOT_DELETE_TOPIC_ERROR);
      return;
    }

    const updatedFlows = flows.filter(flow => flow.from.name !== topicName);

    try {
      const res = await pipelineApi.updatePipeline({
        name,
        group,
        params: {
          name,
          flows: updatedFlows,
        },
      });

      const isSuccess = get(res, 'data.isSuccess', false);

      if (isSuccess) {
        const { name: topicName } = topic;
        showMessage(`${MESSAGES.TOPIC_DELETION_SUCCESS} ${topicName}`);
        refreshGraph();

        const { workspaceName, pipelineName } = props.match.params;
        history.push(`/pipelines/edit/${workspaceName}/${pipelineName}`);
      }
    } catch (error) {
      showMessage(error.message);
    }
  };

  return (
    <>
      {isLoading ? (
        <Box>
          <ListLoader />
        </Box>
      ) : (
        <Box>
          <s.TitleWrapper>
            <s.H5Wrapper>Topic</s.H5Wrapper>
            <Controller
              kind="topic"
              connectorName={topicName}
              onDelete={deleteTopic}
              show={['delete']}
            />
          </s.TitleWrapper>
          <FormGroup data-testid="name">
            <Label>Name</Label>
            <Input name="name" width="100%" value={topic.name} disabled />
          </FormGroup>
        </Box>
      )}
    </>
  );
};

Topic.propTypes = {
  match: PropTypes.shape({
    params: PropTypes.object,
  }).isRequired,
  history: PropTypes.object,
  pipeline: PropTypes.shape({
    name: PropTypes.string.isRequired,
    group: PropTypes.string.isRequired,
    flows: PropTypes.arrayOf(
      PropTypes.shape({
        from: PropTypes.object,
        to: PropTypes.arrayOf(PropTypes.object),
      }),
    ).isRequired,
    tags: PropTypes.shape({
      workerClusterName: PropTypes.string.isRequired,
    }).isRequired,
  }).isRequired,
  graph: PropTypes.arrayOf(graphPropType).isRequired,
  refreshGraph: PropTypes.func.isRequired,
};

export default Topic;
