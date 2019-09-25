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

import React, { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { Form } from 'react-final-form';
import { get, isString, isNull, isEmpty } from 'lodash';

import * as MESSAGES from 'constants/messages';
import * as streamApi from 'api/streamApi';
import * as pipelineApi from 'api/pipelineApi';
import * as utils from './connectorUtils';
import Controller from './Controller';
import AutoSave from './AutoSave';
import useSnackbar from 'components/context/Snackbar/useSnackbar';
import { TitleWrapper, H5Wrapper, LoaderWrap } from './styles';
import { STREAM_APP_ACTIONS } from 'constants/pipelines';
import { ListLoader } from 'components/common/Loader';
import { Box } from 'components/common/Layout';
import { findByGraphName } from '../pipelineUtils';
import { graph as graphPropType } from 'propTypes/pipeline';

const StreamApp = props => {
  const [state, setState] = useState(null);
  const [topics, setTopics] = useState(() =>
    props.pipelineTopics.map(topic => topic.name),
  );
  const [from, setFrom] = useState(null);
  const [configs, setConfigs] = useState(null);
  const [isLoading, setIsLoading] = useState(false);
  const [defs, setDefs] = useState(null);

  const { showMessage } = useSnackbar();

  const { match, pipeline } = props;

  const streamAppName = match.params.connectorName;
  const streamGroup = `${pipeline.tags.workerClusterName}${pipeline.name}`;

  useEffect(() => {
    const fetchStreamApp = async () => {
      const response = await streamApi.fetchProperty(
        streamGroup,
        streamAppName,
      );

      setIsLoading(false);
      const result = get(response, 'data.result', null);

      if (result) {
        const {
          settings,
          definition: { definitions },
        } = result;
        const { from, to } = settings;
        const state = get(result, 'state', null);
        const fromTopic = get(from, '[0].name', '');
        const toTopic = get(to, '[0].name', '');

        const _settings = utils.changeToken({
          values: settings,
          targetToken: '.',
          replaceToken: '_',
        });

        setConfigs({ ..._settings, from: fromTopic, to: toTopic });

        setDefs(definitions);
        setFrom(fromTopic);
        setState(state);
      }
    };

    fetchStreamApp();
  }, [streamAppName, streamGroup]);

  useEffect(() => {
    setTopics(props.pipelineTopics.map(topic => topic.name));
  }, [props.pipelineTopics]);

  const handleSave = async values => {
    const { instances, to } = values;

    let isFromUpdate = false;
    if (values.from !== from) {
      if (values.from !== null) {
        isFromUpdate = true;
      }

      setFrom(values.from);
    }

    const { graph, updateGraph } = props;
    let fromTopic = isString(values.from) ? [values.from] : values.from;
    let toTopic = isString(to) ? [to] : to;

    if (
      (fromTopic && fromTopic[0] === 'Please select...') ||
      isNull(fromTopic)
    ) {
      fromTopic = [];
    }

    if ((toTopic && toTopic[0] === 'Please select...') || isNull(toTopic)) {
      toTopic = [];
    }

    const { workerClusterName } = props.pipeline.tags;
    const topicGroup = workerClusterName;
    const streamJarGroup = workerClusterName;
    const fromKey = isEmpty(fromTopic)
      ? fromTopic
      : [{ group: topicGroup, name: fromTopic[0] }];

    const toKey = isEmpty(toTopic)
      ? toTopic
      : [{ group: topicGroup, name: toTopic[0] }];

    const params = {
      ...values,
      jarKey: { group: streamJarGroup, name: values.jarKey },
      name: streamAppName,
      instances,
      from: fromKey,
      to: toKey,
    };

    const res = await streamApi.updateProperty({
      group: streamGroup,
      name: streamAppName,
      params,
    });
    const isSuccess = get(res, 'data.isSuccess', false);

    if (isSuccess) {
      const [prevFromTopic] = graph.filter(g => g.to.includes(streamAppName));

      // To topic update
      if (!isFromUpdate) {
        const currStreamApp = findByGraphName(graph, streamAppName);
        const toUpdate = { ...currStreamApp, to: toTopic };
        updateGraph({ update: toUpdate, dispatcher: { name: 'STREAM_APP' } });
      } else {
        // From topic update
        let currFromTopic = findByGraphName(graph, values.from);

        let fromUpdate;
        if (currFromTopic) {
          fromUpdate = [...new Set([...currFromTopic.to, streamAppName])];
        } else {
          if (prevFromTopic) {
            fromUpdate = prevFromTopic.to.filter(t => t !== streamAppName);
          } else {
            fromUpdate = [];
          }
          currFromTopic = prevFromTopic;
        }
        let update;
        if (!currFromTopic) {
          update = { ...currFromTopic };
        } else {
          update = {
            ...currFromTopic,
            to: fromUpdate,
          };
        }

        updateGraph({
          dispatcher: { name: 'STREAM_APP' },
          update,
          isFromTopic: true,
          streamAppName,
        });
      }
    }
  };

  const handleStartStreamApp = async () => {
    await triggerStreamApp(STREAM_APP_ACTIONS.start);
  };

  const handleStopStreamApp = async () => {
    await triggerStreamApp(STREAM_APP_ACTIONS.stop);
  };

  const handleDeleteStreamApp = async () => {
    const { refreshGraph, history, pipeline } = props;
    const {
      name: pipelineName,
      flows,
      group: pipelineGroup,
      tags: { workerClusterName },
    } = pipeline;

    if (state) {
      showMessage(
        `The connector is running! Please stop the connector first before deleting`,
      );

      return;
    }

    const connectorResponse = await streamApi.deleteProperty(
      streamGroup,
      streamAppName,
    );

    const connectorHasDeleted = get(connectorResponse, 'data.isSuccess', false);

    const updatedFlows = flows.filter(flow => flow.from.name !== streamAppName);

    const pipelineResponse = await pipelineApi.updatePipeline({
      name: pipelineName,
      group: pipelineGroup,
      params: {
        name: pipelineName,
        flows: updatedFlows,
      },
    });

    const pipelineHasUpdated = get(pipelineResponse, 'data.isSuccess', false);

    if (connectorHasDeleted && pipelineHasUpdated) {
      showMessage(`${MESSAGES.CONNECTOR_DELETION_SUCCESS} ${streamAppName}`);
      await refreshGraph();

      const path = `/pipelines/edit/${workerClusterName}/${pipelineName}`;
      history.push(path);
    }
  };

  const triggerStreamApp = async action => {
    let res;
    if (action === STREAM_APP_ACTIONS.start) {
      res = await streamApi.startStreamApp(streamGroup, streamAppName);
    } else {
      res = await streamApi.stopStreamApp(streamGroup, streamAppName);
    }

    const isSuccess = get(res, 'data.isSuccess', false);
    handleTriggerConnectorResponse(action, isSuccess);
  };

  const handleTriggerConnectorResponse = async (action, isSuccess) => {
    if (!isSuccess) return;

    const response = await streamApi.fetchProperty(streamGroup, streamAppName);
    const state = get(response, 'data.result.state', null);
    const { graph, updateGraph } = props;

    setState({ state });
    const currStreamApp = findByGraphName(graph, streamAppName);
    const update = { ...currStreamApp, state };
    updateGraph({ update, dispatcher: { name: 'STREAM_APP' } });

    if (action === STREAM_APP_ACTIONS.start) {
      if (!isNull(state)) showMessage(MESSAGES.STREAM_APP_START_SUCCESS);
    }
  };

  if (!configs || !defs) return null;

  const { updateHasChanges } = props;
  const formData = utils.getRenderData({
    defs,
    configs,
    state,
  });

  const initialValues = formData.reduce((acc, cur) => {
    acc[cur.key] = cur.displayValue;
    return acc;
  }, {});

  const formProps = {
    formData,
    topics,
  };

  return (
    <Box>
      <TitleWrapper>
        <H5Wrapper>Stream app</H5Wrapper>
        <Controller
          kind="connector"
          connectorName={streamAppName}
          onStart={handleStartStreamApp}
          onStop={handleStopStreamApp}
          onDelete={handleDeleteStreamApp}
        />
      </TitleWrapper>
      {isLoading ? (
        <LoaderWrap>
          <ListLoader />
        </LoaderWrap>
      ) : (
        <Form
          onSubmit={handleSave}
          initialValues={initialValues}
          render={({ values }) => {
            return (
              <form>
                <AutoSave
                  save={handleSave}
                  updateHasChanges={updateHasChanges}
                />

                {utils.renderForm({ parentValues: values, ...formProps })}
              </form>
            );
          }}
        />
      )}
    </Box>
  );
};

StreamApp.propTypes = {
  match: PropTypes.shape({
    params: PropTypes.shape({
      connectorName: PropTypes.string.isRequired,
    }).isRequired,
  }).isRequired,
  graph: PropTypes.arrayOf(graphPropType).isRequired,
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
  updateGraph: PropTypes.func.isRequired,
  refreshGraph: PropTypes.func.isRequired,
  updateHasChanges: PropTypes.func.isRequired,
  pipelineTopics: PropTypes.array.isRequired,
  history: PropTypes.shape({
    push: PropTypes.func.isRequired,
  }).isRequired,
};

export default StreamApp;
