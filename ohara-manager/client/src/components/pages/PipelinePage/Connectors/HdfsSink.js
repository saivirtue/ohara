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
import styled from 'styled-components';
import toastr from 'toastr';
import { Redirect } from 'react-router-dom';
import { get, isEmpty, debounce, includes } from 'lodash';

import * as MESSAGES from 'constants/messages';
import * as connectorApi from 'api/connectorApi';
import * as validateApi from 'api/validateApi';
import * as s from './styles';
import { Box } from 'common/Layout';
import { primaryBtn } from 'theme/btnTheme';
import { Input, Select, FormGroup, Label, Button } from 'common/Form';
import { CONFIGURATION } from 'constants/urls';
import { updateTopic, findByGraphId } from '../pipelineUtils/commonUtils';
import { handleInputChange } from '../pipelineUtils/hdfsSinkUtils';
import Controller from './Controller';
import { HdfsQuicklyFillIn } from './QuicklyFillIn';
import {
  CONNECTOR_TYPES,
  CONNECTOR_STATES,
  CONNECTOR_ACTIONS,
} from 'constants/pipelines';
import { graphPropType } from 'propTypes/pipeline';

const FormGroupCheckbox = styled(FormGroup)`
  flex-direction: row;
  align-items: center;
  color: ${props => props.theme.lightBlue};
`;

const CheckBoxLabel = styled(Label)`
  margin-bottom: 0;
`;

const Checkbox = styled(Input)`
  height: auto;
  width: auto;
  margin-right: 8px;
`;

Checkbox.displayName = 'Checkbox';

class HdfsSink extends React.Component {
  static propTypes = {
    hasChanges: PropTypes.bool.isRequired,
    updateHasChanges: PropTypes.func.isRequired,
    updateGraph: PropTypes.func.isRequired,
    loadGraph: PropTypes.func.isRequired,
    refreshGraph: PropTypes.func.isRequired,
    match: PropTypes.shape({
      params: PropTypes.object.isRequired,
    }).isRequired,
    graph: PropTypes.arrayOf(graphPropType).isRequired,
    history: PropTypes.shape({
      push: PropTypes.func.isRequired,
    }).isRequired,
    topics: PropTypes.array.isRequired,
    isPipelineRunning: PropTypes.bool.isRequired,
  };

  selectMaps = {
    topics: 'currReadTopic',
  };

  state = {
    name: '',
    state: '',
    readTopics: [],
    currReadTopic: {},
    hdfsConnectionUrl: '',
    writePath: '',
    pipelines: {},
    fileEncodings: ['UTF-8'],
    currFileEncoding: {},
    flushLineCount: '',
    rotateInterval: '',
    tempDirectory: '',
    needHeader: true,
    isRedirect: false,
    IsTestConnectionBtnWorking: false,
  };

  componentDidMount() {
    this.fetchData();
  }

  componentDidUpdate(prevProps) {
    const { topics: prevTopics } = prevProps;
    const { connectorId: prevConnectorId } = prevProps.match.params;
    const { hasChanges, topics: currTopics } = this.props;
    const { connectorId: currConnectorId } = this.props.match.params;

    if (prevTopics !== currTopics) {
      this.setState({ writeTopics: currTopics });
    }

    if (prevConnectorId !== currConnectorId) {
      this.fetchData();
    }

    if (hasChanges) {
      this.save();
    }
  }

  fetchData = () => {
    const sinkId = get(this.props.match, 'params.connectorId', null);
    this.fetchSink(sinkId);
  };

  fetchSink = async sinkId => {
    const res = await connectorApi.fetchConnector(sinkId);
    const result = get(res, 'data.result', null);
    const { fileEncodings } = this.state;

    if (result) {
      const { name, state, topics: prevTopics, configs } = result;
      const {
        writePath = '',
        needHeader = false,
        'hdfs.url': hdfsConnectionUrl = '',
        'tmp.dir': tempDirectory = '',
        'flush.line.count': flushLineCount = '',
        'rotate.interval.ms': rotateInterval = '',
        'data.econde': currFileEncoding = fileEncodings[0],
      } = configs;

      const { topics: readTopics } = this.props;

      if (!isEmpty(prevTopics)) {
        const currReadTopic = readTopics.find(
          topic => topic.id === prevTopics[0],
        );

        updateTopic(this.props, currReadTopic, 'sink');
        this.setState({ currReadTopic });
      }

      const _needHeader = needHeader === 'true' ? true : false;

      this.setState({
        name,
        state,
        writePath,
        hdfsConnectionUrl,
        needHeader: _needHeader,
        tempDirectory,
        flushLineCount,
        rotateInterval,
        currFileEncoding,
        readTopics,
      });
    }
  };

  handleInputChange = ({ target: { name, value } }) => {
    this.setState(handleInputChange({ name, value }));
  };

  handleCheckboxChange = ({ target: { name, checked } }) => {
    this.setState(handleInputChange({ name, checked }));
  };

  handleSelectChange = ({ target }) => {
    const { name, options, value } = target;
    const selectedIdx = options.selectedIndex;
    const { id } = options[selectedIdx].dataset;

    const current = this.selectMaps[name];

    this.setState(
      () => {
        return {
          [current]: {
            name: value,
            id,
          },
        };
      },
      () => {
        this.props.updateHasChanges(true);
      },
    );
  };

  save = debounce(async () => {
    const {
      match,
      graph,
      updateHasChanges,
      updateGraph,
      isPipelineRunning,
    } = this.props;
    const {
      name,
      hdfsConnectionUrl,
      currReadTopic,
      writePath,
      needHeader,
      tempDirectory,
      flushLineCount,
      rotateInterval,
      currFileEncoding,
    } = this.state;

    if (isPipelineRunning) {
      toastr.error(MESSAGES.CANNOT_UPDATE_WHILE_RUNNING_ERROR);
      updateHasChanges(false);
      return;
    }

    const sinkId = get(match, 'params.connectorId', null);
    const topics = isEmpty(currReadTopic) ? [] : [currReadTopic.id];

    const params = {
      name,
      schema: [],
      className: CONNECTOR_TYPES.hdfsSink,
      topics,
      numberOfTasks: 1,
      configs: {
        topic: JSON.stringify(currReadTopic),
        needHeader: String(needHeader),
        writePath,
        'datafile.needheader': String(needHeader),
        'datafile.prefix.name': 'part',
        'data.dir': writePath,
        'hdfs.url': hdfsConnectionUrl,
        'tmp.dir': tempDirectory,
        'flush.line.count': flushLineCount,
        'rotate.interval.ms': rotateInterval,
        'data.econde': currFileEncoding,
      },
    };

    await connectorApi.updateConnector({ id: sinkId, params });
    updateHasChanges(false);

    const currTopicId = isEmpty(currReadTopic) ? [] : currReadTopic.id;
    const currSink = findByGraphId(graph, sinkId);
    const topic = findByGraphId(graph, currTopicId);

    let update;
    if (topic) {
      const to = [...new Set([...topic.to, sinkId])];
      update = { ...topic, to };
    } else {
      update = { ...currSink };
    }

    updateGraph({ update, isFromTopic: true, updatedName: name, sinkId });
  }, 1000);

  handleStartConnector = async () => {
    await this.triggerConnector(CONNECTOR_ACTIONS.start);
  };

  handleStopConnector = async () => {
    await this.triggerConnector(CONNECTOR_ACTIONS.stop);
  };

  handleDeleteConnector = async () => {
    const { match, refreshGraph, history } = this.props;
    const { connectorId, pipelineId } = match.params;
    const res = await connectorApi.deleteConnector(connectorId);
    const isSuccess = get(res, 'data.isSuccess', false);

    if (isSuccess) {
      const { name: connectorName } = this.state;
      toastr.success(`${MESSAGES.CONNECTOR_DELETION_SUCCESS} ${connectorName}`);
      await refreshGraph();

      const path = `/pipelines/edit/${pipelineId}`;
      history.push(path);
    }
  };

  triggerConnector = async action => {
    const { match } = this.props;
    const sinkId = get(match, 'params.connectorId', null);
    let res;
    if (action === CONNECTOR_ACTIONS.start) {
      res = await connectorApi.startConnector(sinkId);
    } else {
      res = await connectorApi.stopConnector(sinkId);
    }

    this.handleTriggerConnectorResponse(action, res);
  };

  handleTestConnection = async e => {
    e.preventDefault();
    const { hdfsConnectionUrl: uri } = this.state;

    this.updateIsTestConnectionBtnWorking(true);
    const res = await validateApi.validateHdfs({ uri });
    this.updateIsTestConnectionBtnWorking(false);

    const _res = get(res, 'data.isSuccess', false);

    if (_res) {
      toastr.success(MESSAGES.TEST_SUCCESS);
    }
  };

  updateIsTestConnectionBtnWorking = update => {
    this.setState({ IsTestConnectionBtnWorking: update });
  };

  handleTriggerConnectorResponse = (action, res) => {
    const isSuccess = get(res, 'data.isSuccess', false);
    if (!isSuccess) return;

    const { match, graph, updateGraph } = this.props;
    const sinkId = get(match, 'params.connectorId', null);
    const state = get(res, 'data.result.state');
    this.setState({ state });
    const currSink = findByGraphId(graph, sinkId);
    const update = { ...currSink, state };
    updateGraph({ update });

    if (action === CONNECTOR_ACTIONS.start) {
      if (state === CONNECTOR_STATES.running) {
        toastr.success(MESSAGES.START_CONNECTOR_SUCCESS);
      } else {
        toastr.error(MESSAGES.CANNOT_START_CONNECTOR_ERROR);
      }
    }
  };

  quicklyFillIn = values => {
    this.setState(
      {
        hdfsConnectionUrl: values.url,
      },
      () => {
        this.props.updateHasChanges(true);
      },
    );
  };

  render() {
    const {
      name,
      state,
      readTopics,
      currReadTopic,
      hdfsConnectionUrl,
      writePath,
      needHeader,
      isRedirect,
      fileEncodings,
      currFileEncoding,
      tempDirectory,
      rotateInterval,
      flushLineCount,
      IsTestConnectionBtnWorking,
    } = this.state;

    if (isRedirect) {
      return <Redirect to={CONFIGURATION} />;
    }

    const isRunning = includes(
      [
        CONNECTOR_STATES.running,
        CONNECTOR_STATES.paused,
        CONNECTOR_STATES.failed,
      ],
      state,
    );

    return (
      <Box>
        <s.TitleWrapper>
          <s.H5Wrapper>HDFS sink connector</s.H5Wrapper>
          <Controller
            kind="connector"
            onStart={this.handleStartConnector}
            onStop={this.handleStopConnector}
            onDelete={this.handleDeleteConnector}
          />
        </s.TitleWrapper>
        <form>
          <FormGroup data-testid="name">
            <Label>Name</Label>
            <Input
              name="name"
              width="100%"
              placeholder="HDFS sink name"
              value={name}
              data-testid="name-input"
              handleChange={this.handleInputChange}
              disabled={isRunning}
            />
          </FormGroup>
          <FormGroup data-testid="read-from-topic">
            <Label>Read topic</Label>
            <Select
              isObject
              name="topics"
              list={readTopics}
              selected={currReadTopic}
              width="100%"
              data-testid="topic-select"
              handleChange={this.handleSelectChange}
              disabled={isRunning}
              placeholder="Please select a topic..."
              clearable
            />
          </FormGroup>

          <FormGroup data-testid="hdfsConnectionUrl">
            <Label>Connection URL</Label>
            <Input
              name="hdfsConnectionUrl"
              width="100%"
              placeholder="file://"
              value={hdfsConnectionUrl}
              data-testid="hdfs-connection-url-input"
              handleChange={this.handleInputChange}
              disabled={isRunning}
            />

            {/* Incomplete feature, don't display this for now */}
            {false && <HdfsQuicklyFillIn onFillIn={this.quicklyFillIn} />}
          </FormGroup>

          <FormGroup data-testid="write-path">
            <Label>Write path</Label>
            <Input
              name="writePath"
              width="100%"
              placeholder="file://"
              value={writePath}
              data-testid="write-path-input"
              handleChange={this.handleInputChange}
              disabled={isRunning}
            />
          </FormGroup>

          <FormGroup data-testid="temp-directory">
            <Label>Temp directory</Label>
            <Input
              name="tempDirectory"
              width="100%"
              placeholder="/tmp"
              value={tempDirectory}
              data-testid="temp-directory"
              handleChange={this.handleInputChange}
              disabled={isRunning}
            />
          </FormGroup>

          <FormGroup data-testid="file-encoding">
            <Label>File encoding</Label>
            <Select
              name="fileEncoding"
              width="100%"
              data-testid="file-enconding-select"
              selected={currFileEncoding}
              list={fileEncodings}
              handleChange={this.handleSelectChange}
              disabled={isRunning}
            />
          </FormGroup>

          <FormGroup data-testid="rotate-interval">
            <Label>Rotate interval (ms)</Label>
            <Input
              name="rotateInterval"
              width="100%"
              placeholder="60000"
              value={rotateInterval}
              data-testid="rotate-interval"
              handleChange={this.handleInputChange}
              disabled={isRunning}
            />
          </FormGroup>

          <FormGroup data-testid="flush-line-count">
            <Label>Flush line count</Label>
            <Input
              name="flushLineCount"
              width="100%"
              placeholder="10"
              value={flushLineCount}
              data-testid="flush-line-count"
              handleChange={this.handleInputChange}
              disabled={isRunning}
            />
          </FormGroup>

          <FormGroupCheckbox data-testid="need-header">
            <Checkbox
              type="checkbox"
              name="needHeader"
              width="25px"
              value=""
              checked={needHeader}
              data-testid="needheader-input"
              handleChange={this.handleCheckboxChange}
              disabled={isRunning}
            />
            <CheckBoxLabel>Include header</CheckBoxLabel>
          </FormGroupCheckbox>

          <FormGroup>
            <Button
              theme={primaryBtn}
              text="Test connection"
              isWorking={IsTestConnectionBtnWorking}
              disabled={IsTestConnectionBtnWorking || isRunning}
              data-testid="test-connection-btn"
              handleClick={this.handleTestConnection}
            />
          </FormGroup>
        </form>
      </Box>
    );
  }
}

export default HdfsSink;
