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

/* eslint-disable no-unused-expressions */
/* eslint-disable @typescript-eslint/no-unused-expressions */
// eslint is complaining about `expect(thing).to.be.undefined`

import { KIND } from '../../src/const';
import * as generate from '../../src/utils/generate';
import * as topicApi from '../../src/api/topicApi';
import * as connectorApi from '../../src/api/connectorApi';
import * as pipelineApi from '../../src/api/pipelineApi';
import { createServicesInNodes, deleteAllServices } from '../utils';
import { SOURCES, SINKS } from '../../src/api/apiInterface/connectorInterface';

const generatePipeline = async () => {
  const { node, broker, worker } = await createServicesInNodes({
    withWorker: true,
    withBroker: true,
    withZookeeper: true,
  });
  const topic = {
    name: generate.serviceName({ prefix: 'topic' }),
    group: generate.serviceName({ prefix: 'group' }),
    nodeNames: [node.hostname],
    brokerClusterKey: {
      name: broker.name,
      group: broker.group,
    },
  };
  await topicApi.create(topic);

  const connectorSource = {
    name: generate.serviceName({ prefix: 'connector' }),
    group: generate.serviceName({ prefix: 'group' }),
    connector__class: SOURCES.perf,
    topicKeys: [{ name: topic.name, group: topic.group }],
    workerClusterKey: {
      name: worker.name,
      group: worker.group,
    },
  };
  await connectorApi.create(connectorSource);

  const connectorSink = {
    name: generate.serviceName({ prefix: 'connector' }),
    group: generate.serviceName({ prefix: 'group' }),
    connector__class: SINKS.console,
    topicKeys: [{ name: topic.name, group: topic.group }],
    workerClusterKey: {
      name: worker.name,
      group: worker.group,
    },
  };
  await connectorApi.create(connectorSink);

  const pipelineName = generate.serviceName({ prefix: 'pipeline' });
  const pipeline = {
    name: pipelineName,
    group: generate.serviceName({ prefix: 'group' }),
    objects: [],
    jarKeys: [],
    lastModified: 0,
    endpoints: [
      {
        name: connectorSource.name,
        group: connectorSource.group,
        kind: KIND.source,
      },
      {
        name: topic.name,
        group: topic.group,
        kind: KIND.topic,
      },
    ],
    tags: {
      name: pipelineName,
    },
  };
  return pipeline;
};

describe('Pipeline API', () => {
  beforeEach(() => deleteAllServices());

  it('createPipeline', async () => {
    const pipeline = await generatePipeline();
    const result = await pipelineApi.create(pipeline);

    const { endpoints, name, group, objects, lastModified, tags } = result.data;
    expect(endpoints).to.be.an('array');
    expect(endpoints.sort()).to.be.deep.eq(pipeline.endpoints.sort());

    expect(lastModified).to.be.a('number');

    expect(name).to.be.a('string');
    expect(name).to.eq(pipeline.name);

    expect(group).to.be.a('string');
    expect(group).to.eq(pipeline.group);

    expect(objects).to.be.an('array');
    expect(objects).have.lengthOf(2);

    expect(tags.name).to.eq(pipeline.name);
  });

  it('fetchPipeline', async () => {
    const pipeline = await generatePipeline();
    await pipelineApi.create(pipeline);

    const result = await pipelineApi.get(pipeline);

    const { endpoints, name, group, objects, lastModified, tags } = result.data;

    expect(endpoints).to.be.an('array');
    expect(endpoints.sort()).to.be.deep.eq(pipeline.endpoints.sort());

    expect(lastModified).to.be.a('number');

    expect(name).to.be.a('string');
    expect(name).to.eq(pipeline.name);

    expect(group).to.be.a('string');
    expect(group).to.eq(pipeline.group);

    expect(objects).to.be.an('array');
    expect(objects).have.lengthOf(2);

    expect(tags.name).to.eq(pipeline.name);
  });

  it('fetchPipelines', async () => {
    const pipelineOne = await generatePipeline();
    const pipelineTwo = await generatePipeline();

    await pipelineApi.create(pipelineOne);
    await pipelineApi.create(pipelineTwo);

    const result = await pipelineApi.getAll();

    const pipelines = result.data.map((pipeline) => pipeline.name);
    expect(pipelines.includes(pipelineOne.name)).to.be.true;
    expect(pipelines.includes(pipelineTwo.name)).to.be.true;
  });

  it('deletePipeline', async () => {
    const pipeline = await generatePipeline();
    await pipelineApi.create(pipeline);

    await pipelineApi.remove(pipeline);
    const result = await pipelineApi.getAll();

    expect(result.data.map((p) => p.name).includes(pipeline.name)).to.be.false;
  });

  it('updatePipeline', async () => {
    const pipeline = await generatePipeline();
    const newParams = {
      endpoints: [],
    };
    const newPipeline = { ...pipeline, ...newParams };

    await pipelineApi.create(pipeline);

    const result = await pipelineApi.update(newPipeline);

    const { endpoints, name, group, objects, lastModified, tags } = result.data;

    expect(endpoints).to.be.an('array');
    expect(endpoints).have.lengthOf(0);

    expect(lastModified).to.be.a('number');

    expect(name).to.be.a('string');
    expect(name).to.eq(pipeline.name);

    expect(group).to.be.a('string');
    expect(group).to.eq(pipeline.group);

    expect(objects).to.be.an('array');
    expect(objects).have.lengthOf(0);

    expect(tags.name).to.eq(pipeline.name);
  });

  it('refreshPipeline', async () => {
    const pipeline = await generatePipeline();
    const result = await pipelineApi.create(pipeline);

    const source = result.data.objects.find((obj) => obj.kind === KIND.source);
    expect(source).to.be.not.undefined;

    if (source) {
      await connectorApi.stop({ name: source.name, group: source.group });
      await connectorApi.remove({ name: source.name, group: source.group });
    }

    await pipelineApi.refresh(pipeline);
    const refreshResult = await pipelineApi.get(pipeline);

    const {
      endpoints,
      name,
      group,
      objects,
      lastModified,
      tags,
    } = refreshResult.data;

    expect(endpoints).to.be.an('array');
    expect(endpoints).have.lengthOf(1);

    expect(lastModified).to.be.a('number');

    expect(name).to.be.a('string');
    expect(name).to.eq(pipeline.name);

    expect(group).to.be.a('string');
    expect(group).to.eq(pipeline.group);

    expect(objects).to.be.an('array');
    expect(objects).have.lengthOf(1);

    expect(tags.name).to.eq(pipeline.name);
  });
});
