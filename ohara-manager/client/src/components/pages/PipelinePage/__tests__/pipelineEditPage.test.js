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
import { cleanup, waitForElement, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom/extend-expect';

import * as generate from 'utils/generate';
import * as pipelineApi from 'api/pipelineApi';
import * as connectorApi from 'api/connectorApi';
import * as workerApi from 'api/workerApi';
import * as topicApi from 'api/topicApi';
import PipelineEditPage from '../PipelineEditPage';
import { CONNECTOR_TYPES } from 'constants/pipelines';
import { PIPELINE_EDIT } from 'constants/documentTitles';
import { renderWithProvider } from 'utils/testUtils';

jest.mock('api/pipelineApi');
jest.mock('api/connectorApi');
jest.mock('api/workerApi');
jest.mock('api/topicApi');

// Mock the entire graph component for now. As we don't know how
// to test it yet...
jest.mock('../PipelineGraph', () => () => <span>Graph</span>);

const setup = () => {
  const props = {
    match: {
      params: {
        pipelineName: generate.name(),
        connectorName: generate.name(),
      },
    },
  };

  workerApi.fetchWorker.mockImplementation(() =>
    Promise.resolve({
      data: {
        result: {
          connectors: generate.connectors(),
        },
      },
    }),
  );

  const topicName = generate.name();
  const sourceName = generate.name();

  const pipeline = {
    flows: [
      { from: { group: 'abc', name: topicName }, to: [] },
      { from: { group: 'cde', name: sourceName }, to: [] },
    ],
    name: generate.name(),
    tags: {
      workerClusterName: generate.name(),
    },
    objects: [
      {
        kind: 'topic',
        name: topicName,
        className: CONNECTOR_TYPES.topic,
      },
      {
        kind: 'source',
        name: sourceName,
        state: 'RUNNING',
        className: CONNECTOR_TYPES.ftpSource,
      },
    ],
  };

  const topics = generate.topics({ count: 1 });

  pipelineApi.fetchPipeline.mockImplementation(() =>
    Promise.resolve({
      data: {
        result: pipeline,
      },
    }),
  );

  topicApi.fetchTopics.mockImplementation(() =>
    Promise.resolve({
      data: {
        result: topics,
      },
    }),
  );

  return {
    props,
    pipeline,
  };
};

afterEach(cleanup);

describe('<PipelineEditPage />', () => {
  it('renders self', async () => {
    const { props } = setup();

    await waitForElement(() =>
      renderWithProvider(<PipelineEditPage {...props} />),
    );
  });

  it('renders edit pipeline page document title, if pipelineName is present', async () => {
    const { props } = setup();

    await waitForElement(() =>
      renderWithProvider(<PipelineEditPage {...props} />),
    );
    expect(document.title).toBe(PIPELINE_EDIT);
  });

  it('display the pipeline name', async () => {
    const { pipeline, props } = setup();

    const { getByText } = await waitForElement(() =>
      renderWithProvider(<PipelineEditPage {...props} />),
    );
    getByText(pipeline.name);
  });

  it('renders Toolbar', async () => {
    const { props } = setup();

    const { getByText, getByTestId } = await waitForElement(() =>
      renderWithProvider(<PipelineEditPage {...props} />),
    );
    getByTestId('toolbar-sources');
    getByTestId('toolbar-topics');
    getByTestId('toolbar-streams');
    getByTestId('toolbar-sinks');
    getByText('All changes saved');
  });

  it('renders Pipeline operate', async () => {
    const { props, pipeline } = setup();

    const { getByText, getByTestId } = await waitForElement(() =>
      renderWithProvider(<PipelineEditPage {...props} />),
    );
    getByText('Operate');
    getByTestId('start-btn');
    getByTestId('stop-btn');
    getByText(
      'This pipeline is running on: ' + pipeline.tags.workerClusterName,
    );
  });

  it('starts the pipeline', async () => {
    const { props } = setup();
    const group = generate.name();

    const data = {
      result: {
        name: 'test',
        group,
        objects: [
          { kind: 'source', name: 'c', className: CONNECTOR_TYPES.ftpSource },
          { kind: 'sink', name: 'b', className: CONNECTOR_TYPES.ftpSink },
          { kind: 'topic', name: 'a', className: CONNECTOR_TYPES.topic },
        ],
        flows: [
          { from: { group: 'abc', name: 'c' }, to: [] },
          { from: { group: 'def', name: 'b' }, to: [] },
          { from: { group: 'xdf', name: 'a' }, to: [] },
        ],
        tags: {
          workerClusterName: generate.name(),
        },
      },
    };

    pipelineApi.fetchPipeline.mockImplementation(() =>
      Promise.resolve({ data }),
    );

    connectorApi.startConnector.mockImplementation(() =>
      Promise.resolve({ data: { isSuccess: true } }),
    );

    const { getByTestId } = await waitForElement(() =>
      renderWithProvider(<PipelineEditPage {...props} />),
    );

    // Start the pipeline
    fireEvent.click(await waitForElement(() => getByTestId('start-btn')));

    expect(connectorApi.startConnector).toHaveBeenCalledTimes(2);
    expect(connectorApi.startConnector).toHaveBeenCalledWith(
      group,
      data.result.objects[0].name,
    );
    expect(connectorApi.startConnector).toHaveBeenCalledWith(
      group,
      data.result.objects[1].name,
    );
  });

  it('stops the pipeline', async () => {
    const { props } = setup();

    const group = generate.name();

    const data = {
      result: {
        name: 'test',
        objects: [
          { kind: 'source', name: 'c', className: CONNECTOR_TYPES.ftpSource },
          { kind: 'sink', name: 'b', className: CONNECTOR_TYPES.ftpSink },
          { kind: 'topic', name: 'a', className: CONNECTOR_TYPES.topic },
        ],
        flows: [
          { from: { group: 'abc', name: 'c' }, to: [] },
          { from: { group: 'def', name: 'b' }, to: [] },
          { from: { group: 'efx', name: 'a' }, to: [] },
        ],
        group,
        tags: {
          workerClusterName: generate.name(),
        },
      },
    };

    pipelineApi.fetchPipeline.mockImplementation(() =>
      Promise.resolve({ data }),
    );

    connectorApi.stopConnector.mockImplementation(() =>
      Promise.resolve({ data: { isSuccess: true } }),
    );

    const { getByTestId } = await waitForElement(() =>
      renderWithProvider(<PipelineEditPage {...props} />),
    );

    // Stop the pipeline
    fireEvent.click(await waitForElement(() => getByTestId('stop-btn')));

    expect(connectorApi.stopConnector).toHaveBeenCalledTimes(2);
    expect(connectorApi.stopConnector).toHaveBeenCalledWith(
      group,
      data.result.objects[0].name,
    );
    expect(connectorApi.stopConnector).toHaveBeenCalledWith(
      group,
      data.result.objects[1].name,
    );
  });
});
