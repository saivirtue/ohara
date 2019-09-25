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

import * as generate from '../../src/utils/generate';

const setup = () => {
  const nodeName = generate.serviceName({ prefix: 'node' });
  const zookeeperClusterName = generate.serviceName({ prefix: 'zk' });
  const brokerClusterName = generate.serviceName({ prefix: 'bk' });
  const workerClusterName = generate.serviceName({ prefix: 'wk' });
  const pipelineName = generate.serviceName({ prefix: 'pi' });

  const topicGroup = workerClusterName;
  const pipelineGroup = `${workerClusterName}${pipelineName}`;

  cy.createNode({
    name: nodeName,
    port: generate.port(),
    user: generate.userName(),
    password: generate.password(),
  });

  cy.createZookeeper({
    name: zookeeperClusterName,
    nodeNames: [nodeName],
  });

  cy.startZookeeper(zookeeperClusterName);

  cy.createBroker({
    name: brokerClusterName,
    nodeNames: [nodeName],
    zookeeperClusterName,
  });

  cy.startBroker(brokerClusterName);

  cy.createWorker({
    name: workerClusterName,
    nodeNames: [nodeName],
    brokerClusterName,
  });

  cy.createPipeline({
    name: pipelineName,
    group: pipelineGroup,
    tags: {
      workerClusterName,
    },
  }).as('createPipeline');

  return {
    zookeeperClusterName,
    brokerClusterName,
    workerClusterName,
    pipelineName,
    pipelineGroup,
    topicGroup,
  };
};

describe('Pipeline API', () => {
  beforeEach(() => cy.deleteAllServices());

  it('createPipeline', () => {
    const { pipelineName, workerClusterName, pipelineGroup } = setup();

    cy.get('@createPipeline').then(response => {
      const {
        data: { isSuccess, result },
      } = response;

      expect(isSuccess).to.eq(true);

      expect(result.name).to.eq(pipelineName);
      expect(result.tags.workerClusterName).to.eq(workerClusterName);
      expect(result.objects).to.be.an('array');
      expect(result.flows).to.be.an('array');
      expect(result.group).to.eq(pipelineGroup);
    });
  });

  it('fetchPipeline', () => {
    const { pipelineName, workerClusterName, pipelineGroup } = setup();

    cy.fetchPipeline(pipelineGroup, pipelineName).then(response => {
      const {
        data: { isSuccess, result },
      } = response;

      expect(isSuccess).to.eq(true);

      expect(result.name).to.eq(pipelineName);
      expect(result.tags.workerClusterName).to.eq(workerClusterName);
      expect(result.objects).to.be.an('array');
      expect(result.flows).to.be.an('array');
      expect(result.group).to.eq(pipelineGroup);
    });
  });

  it('fetchPipelines', () => {
    const { workerClusterName } = setup();

    const paramsOne = {
      name: generate.serviceName({ prefix: 'pi' }),
      workerClusterName,
    };

    const paramsTwo = {
      name: generate.serviceName({ prefix: 'pi' }),
      workerClusterName,
    };

    cy.createPipeline(paramsOne);
    cy.createPipeline(paramsTwo);

    cy.fetchPipelines().then(response => {
      const {
        data: { isSuccess, result },
      } = response;

      expect(isSuccess).to.eq(true);

      const pipelines = result.filter(
        pipeline =>
          pipeline.name === paramsOne.name || pipeline.name === paramsTwo.name,
      );

      expect(pipelines.length).to.eq(2);
    });
  });

  it('updatePipeline', () => {
    const {
      brokerClusterName,
      workerClusterName,
      pipelineName,
      pipelineGroup,
      topicGroup,
    } = setup();

    let topicName = generate.serviceName({ prefix: 'topic' });

    cy.createTopic({
      name: topicName,
      group: topicGroup,
      brokerClusterName,
    });

    cy.startTopic(topicGroup, topicName);

    const params = {
      name: pipelineName,
      group: pipelineGroup,
      params: {
        flows: [{ from: { group: topicGroup, name: topicName }, to: [] }],
        workerClusterName,
      },
    };

    cy.updatePipeline(params).then(response => {
      const {
        data: { isSuccess, result },
      } = response;

      expect(isSuccess).to.eq(true);

      expect(result.name).to.eq(pipelineName);
      expect(result.tags.workerClusterName).to.eq(workerClusterName);
      expect(result.objects).to.be.an('array');
      expect(result.flows).to.be.an('array');

      const [topic] = result.objects;

      expect(topic.kind).to.eq('topic');
      expect(topic.name).to.eq(topicName);
      expect(topic.metrics).to.be.an('object');
      expect(topic.metrics.meters).to.be.an('array');
      expect(result.group).to.eq(pipelineGroup);
    });
  });

  it('deletePipeline', () => {
    const { pipelineName, pipelineGroup } = setup();

    cy.deletePipeline(pipelineGroup, pipelineName).then(response => {
      expect(response.data.isSuccess).to.eq(true);
    });
  });
});
