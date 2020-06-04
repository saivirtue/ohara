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

import { KIND, CELL_TYPES } from '../../src/const';
import * as generate from '../../src/utils/generate';
import { deleteAllServices } from '../utils';
import { SOURCES, SINKS } from '../../src/api/apiInterface/connectorInterface';

const ACTIONS = {
  link: 'link',
  config: 'config',
  remove: 'remove',
};
const sources = Object.values(SOURCES).sort();
const sinks = Object.values(SINKS).sort();

describe('ToolBox of Pipeline', () => {
  before(async () => await deleteAllServices());

  it('create an empty pipeline should work normally', () => {
    cy.createWorkspace({});

    // Add new pipeline
    cy.findByText(/^pipelines$/i)
      .siblings('svg')
      .first()
      .click();
    cy.findByText(/^add a new pipeline$/i).should('exist');

    cy.findByTestId('new-pipeline-dialog').find('input').type('pipeline1');

    cy.findByText(/^add$/i).click();
  });

  it('check the toolbox works normally', () => {
    // wait 20s for the connectors loaded into toolbox
    cy.wait(20000);

    cy.visit('/');

    cy.findAllByText(/^wo$/i).should('exist');

    // check the toolbox
    cy.findByText(/^toolbox$/i).should('exist');

    cy.findByText(/^source$/i)
      .should('exist')
      .click();
    Object.values(sources).forEach((clz) => {
      const name = clz.slice(clz.lastIndexOf('.') + 1);
      cy.findByText(name).should('exist');
    });

    cy.findByText(/^topic$/i)
      .should('exist')
      .click();

    cy.findByText(/^stream$/i)
      .should('exist')
      .click();

    cy.findByText(/^sink$/i)
      .should('exist')
      .click();
    Object.values(sinks).forEach((clz) => {
      const name = clz.slice(clz.lastIndexOf('.') + 1);
      cy.findByText(name).should('exist');
    });

    // check the toolbox quick icon
    cy.findByText(/^insert$/i)
      .should('exist')
      .siblings('div')
      .first()
      .within(() => {
        cy.get('button').each((el) => cy.wrap(el).click());
      });
    //after all clicks, the sink connector list should be visible
    cy.contains('span:visible', 'PerfSource').should('not.exist');
    cy.contains('span:visible', 'ConsoleSink').should('exist');

    // filter components in toolBox
    cy.findAllByPlaceholderText('Search topic & connector...')
      .filter(':visible')
      .type('ftp');
    cy.contains('span:visible', 'FtpSource').should('exist');
    cy.contains('span:visible', 'FtpSink').should('exist');

    cy.findAllByPlaceholderText('Search topic & connector...')
      .filter(':visible')
      .clear()
      .type('console');
    cy.contains('span:visible', 'FtpSource').should('not.exist');
    cy.contains('span:visible', 'ConsoleSink').should('exist');

    cy.findAllByPlaceholderText('Search topic & connector...')
      .filter(':visible')
      .clear()
      .type('fake');
    cy.contains('span:visible', 'FtpSource').should('not.exist');
    cy.contains('span:visible', 'FtpSink').should('not.exist');
  });
});

// TODO in https://github.com/oharastream/ohara/issues/4049
describe.skip('Element Links of Pipeline', () => {
  before(async () => await deleteAllServices());
  it('tests of connector and topic links in pipeline', () => {
    cy.createWorkspace({});

    // Add new pipeline
    cy.findByText(/^pipelines$/i)
      .siblings('svg')
      .first()
      .click();
    cy.findByText(/^add a new pipeline$/i).should('exist');

    cy.findByTestId('new-pipeline-dialog').find('input').type('pipeline1');

    cy.findByText(/^add$/i).click();

    // wait 20s for the connectors loaded into toolbox
    cy.wait(20000);

    // force to reload the page in order to get the correct data in toolbox
    cy.reload();

    // check the toolbox
    cy.findByText(/^toolbox$/i).should('exist');

    // conditional tests of element link
    const elements = {
      perfSourceName: generate.serviceName({ prefix: 'source' }),
      ftpSourceName: generate.serviceName({ prefix: 'source' }),
      consoleSinkName: generate.serviceName({ prefix: 'sink' }),
      hdfsSinkName: generate.serviceName({ prefix: 'sink' }),
      pipelineOnlyTopicName1: 'T1',
      pipelineOnlyTopicName2: 'T2',
    };

    cy.log('add elements:');
    cy.log(`element: perf source ${elements.perfSourceName}`);
    cy.log(`element: ftp source ${elements.ftpSourceName}`);
    cy.log(`element: pipeline-only topic 1 ${elements.pipelineOnlyTopicName1}`);
    cy.log(`element: console sink ${elements.consoleSinkName}`);
    cy.log(`element: pipeline-only topic 2 ${elements.pipelineOnlyTopicName2}`);
    cy.log(`element: hdfs sink ${elements.hdfsSinkName}`);
    cy.addElement(elements.perfSourceName, KIND.source, SOURCES.perf);
    cy.addElement(elements.ftpSourceName, KIND.source, SOURCES.ftp);
    cy.addElement(elements.pipelineOnlyTopicName1, KIND.topic);
    cy.addElement(elements.consoleSinkName, KIND.sink, SINKS.console);
    cy.addElement(elements.pipelineOnlyTopicName2, KIND.topic);
    cy.addElement(elements.hdfsSinkName, KIND.sink, SINKS.hdfs);

    // let the backend API makes effect
    cy.wait(5000);

    // to get the actual data-testid
    // we need to refresh the paper again
    cy.reload();

    // 1. perf source -> ftp source
    cy.log(`cannot create a link from perf source to ftp source`);
    cy.getCell(elements.perfSourceName).trigger('mouseover');
    cy.cellAction(elements.perfSourceName, ACTIONS.link).click();
    cy.getCell(elements.ftpSourceName).click();
    cy.findByText(`Target ${elements.ftpSourceName} is a source!`)
      .should('exist')
      .siblings('div')
      .first()
      .click();

    // 2. pipeline-only topic 1 -> pipeline-only topic 2
    cy.log(`cannot create a link between pipeline-only topic 1 and 2`);
    cy.getCell(elements.pipelineOnlyTopicName1).trigger('mouseover');
    cy.cellAction(elements.pipelineOnlyTopicName1, ACTIONS.link).click();
    cy.getCell(elements.pipelineOnlyTopicName2).click();
    cy.findByText(
      `Cannot connect a ${KIND.topic} to another ${KIND.topic}, they both have the same type`,
    )
      .should('exist')
      .siblings('div')
      .first()
      .click();

    // 3. pipeline-only topic 1 -> hdfs sink
    cy.log(
      `create a link from a perf source to pipeline-only topic 1 to hdfs sink`,
    );
    cy.getCell(elements.perfSourceName).trigger('mouseover');
    cy.cellAction(elements.perfSourceName, ACTIONS.link).click();
    cy.getCell(elements.pipelineOnlyTopicName1).click();
    cy.getCell(elements.pipelineOnlyTopicName1).trigger('mouseover');
    cy.cellAction(elements.pipelineOnlyTopicName1, ACTIONS.link).click();
    cy.getCell(elements.hdfsSinkName).click();
    // will create two links
    cy.get(`g[data-type="${CELL_TYPES.LINK}"]`).should('have.length', 2);

    // 4. ftp source -> hdfs sink
    cy.log(`cannot create a link from ftp source to hdfs sink`);
    cy.getCell(elements.ftpSourceName).trigger('mouseover');
    cy.cellAction(elements.ftpSourceName, ACTIONS.link).click();
    cy.getCell(elements.hdfsSinkName).click();
    cy.findByText(
      `The target ${elements.hdfsSinkName} is already connected to a source`,
    )
      .should('exist')
      .siblings('div')
      .first()
      .click();

    // 5. pipeline-only topic 2 -> hdfs sink
    cy.log(`cannot create a link from pipeline-only topic to hdfs sink`);
    cy.getCell(elements.pipelineOnlyTopicName2).trigger('mouseover');
    cy.cellAction(elements.pipelineOnlyTopicName2, ACTIONS.link).click();
    cy.getCell(elements.hdfsSinkName).click();
    cy.findByText(
      `The target ${elements.hdfsSinkName} is already connected to a source`,
    )
      .should('exist')
      .siblings('div')
      .first()
      .click();

    // we can force delete a connected topic
    cy.log(`force delete pipeline-only topic`);
    cy.getCell(elements.pipelineOnlyTopicName1).trigger('mouseover');
    cy.cellAction(elements.pipelineOnlyTopicName1, ACTIONS.remove).click();
    cy.findByText(/^delete$/i).should('exist');
    cy.contains('span:visible', /cancel/i).click();
    cy.findAllByText(elements.pipelineOnlyTopicName1).should('exist');

    // delete all elements
    Object.values(elements).forEach((element) => {
      cy.getCell(element).trigger('mouseover');
      cy.cellAction(element, ACTIONS.remove).click();
      cy.findByText(/^delete$/i).click();
      cy.findAllByText(element).filter(':visible').should('not.exist');
      cy.wait(2000);
    });
  });

  it('tests of stream, connector and topic links in pipeline', () => {
    cy.visit('/');

    // check the toolbox
    cy.findByText(/^toolbox$/i).should('exist');

    // conditional tests of element link
    const elements = {
      perfSourceName: generate.serviceName({ prefix: 'source' }),
      ftpSourceName: generate.serviceName({ prefix: 'source' }),
      consoleSinkName: generate.serviceName({ prefix: 'sink' }),
      hdfsSinkName: generate.serviceName({ prefix: 'sink' }),
      topicName1: generate.serviceName({ prefix: 'topic' }),
      streamName: generate.serviceName({ prefix: 'stream' }),
      topicName2: generate.serviceName({ prefix: 'topic' }),
    };

    cy.log('add elements:');
    cy.log(`element: perf source ${elements.perfSourceName}`);
    cy.log(`element: ftp source ${elements.ftpSourceName}`);
    cy.log(`element: console sink ${elements.consoleSinkName}`);
    cy.log(`element: hdfs sink ${elements.hdfsSinkName}`);
    cy.log(`element: shared topic 1 ${elements.topicName1}`);
    cy.log(`element: stream ${elements.streamName}`);
    cy.log(`element: shared topic 2 ${elements.topicName2}`);
    cy.addElement(elements.perfSourceName, KIND.source, SOURCES.perf);
    cy.addElement(elements.ftpSourceName, KIND.source, SOURCES.ftp);
    cy.addElement(elements.consoleSinkName, KIND.sink, SINKS.console);
    cy.addElement(elements.hdfsSinkName, KIND.sink, SINKS.hdfs);
    cy.addElement(elements.topicName1, KIND.topic);
    cy.addElement(elements.streamName, KIND.stream);
    cy.addElement(elements.topicName2, KIND.topic);

    // let the backend API makes effect
    cy.wait(5000);

    // to get the actual data-testid
    // we need to refresh the paper again
    cy.reload();

    // 1. perf source -> topic1 -> stream -> topic2 -> hdfs sink
    cy.log(
      `create a link from perf source to topic 1 to stream to topic 2 to hdfs sink`,
    );
    cy.getCell(elements.perfSourceName).trigger('mouseover');
    cy.cellAction(elements.perfSourceName, ACTIONS.link).click();
    cy.getCell(elements.topicName1).click();

    cy.getCell(elements.topicName1).trigger('mouseover');
    cy.cellAction(elements.topicName1, ACTIONS.link).click();
    cy.getCell(elements.streamName).click();

    cy.getCell(elements.streamName).trigger('mouseover');
    cy.cellAction(elements.streamName, ACTIONS.link).click();
    cy.getCell(elements.topicName2).click();

    cy.getCell(elements.topicName2).trigger('mouseover');
    cy.cellAction(elements.topicName2, ACTIONS.link).click();
    cy.getCell(elements.hdfsSinkName).click();
    // will create four lines
    cy.get(`g[data-type="${CELL_TYPES.LINK}"]`).should('have.length', 4);

    // ftp source -> stream
    cy.log(`cannot create a link from ftp source to stream`);
    cy.getCell(elements.ftpSourceName).trigger('mouseover');
    cy.cellAction(elements.ftpSourceName, ACTIONS.link).click();
    cy.getCell(elements.streamName).click();
    cy.findByText(
      `The target ${elements.streamName} is already connected to a source`,
    )
      .should('exist')
      .siblings('div')
      .first()
      .click();
  });
});

// TODO in https://github.com/oharastream/ohara/issues/4049
describe.skip('Topic Operations of Pipeline', () => {
  before(async () => await deleteAllServices());
  it('connect two elements of pipeline should auto generate pipeline-only topic', () => {
    cy.createWorkspace({});

    // Add new pipeline
    cy.findByText(/^pipelines$/i)
      .siblings('svg')
      .first()
      .click();
    cy.findByText(/^add a new pipeline$/i).should('exist');

    cy.findByTestId('new-pipeline-dialog').find('input').type('pipeline1');

    cy.findByText(/^add$/i).click();

    // force to reload the page in order to get the correct data in toolbox
    cy.reload();

    // check the toolbox
    cy.findByText(/^toolbox$/i).should('exist');

    const elements = {
      perfSourceName: generate.serviceName({ prefix: 'source' }),
      consoleSinkName: generate.serviceName({ prefix: 'sink' }),
      ftpSourceName: generate.serviceName({ prefix: 'source' }),
      hdfsSinkName: generate.serviceName({ prefix: 'sink' }),
      streamName1: generate.serviceName({ prefix: 'stream' }),
      streamName2: generate.serviceName({ prefix: 'stream' }),
      streamName3: generate.serviceName({ prefix: 'stream' }),
    };

    cy.log('add elements:');
    cy.log(`element: perf source ${elements.perfSourceName}`);
    cy.log(`element: console sink ${elements.consoleSinkName}`);
    cy.log(`element: ftp source ${elements.ftpSourceName}`);
    cy.log(`element: hdfs sink ${elements.hdfsSinkName}`);
    cy.log(`element: stream1 ${elements.streamName1}`);
    cy.log(`element: stream2 ${elements.streamName2}`);
    cy.log(`element: stream3 ${elements.streamName3}`);
    cy.addElement(elements.perfSourceName, KIND.source, SOURCES.perf);
    cy.addElement(elements.consoleSinkName, KIND.sink, SINKS.console);
    cy.addElement(elements.ftpSourceName, KIND.source, SOURCES.ftp);
    cy.addElement(elements.hdfsSinkName, KIND.sink, SINKS.hdfs);
    cy.addElement(elements.streamName1, KIND.stream);
    cy.addElement(elements.streamName2, KIND.stream);
    cy.addElement(elements.streamName3, KIND.stream);

    // let the backend API makes effect
    cy.wait(5000);

    // to get the actual data-testid
    // we need to refresh the paper again
    cy.reload();

    cy.log(`auto generate topic for perf source -> console sink`);
    cy.getCell(elements.perfSourceName).trigger('mouseover');
    cy.cellAction(elements.perfSourceName, ACTIONS.link).click();
    cy.getCell(elements.consoleSinkName).click();

    // generate "T1" pipeline-only topic
    cy.findAllByText('T1').should('exist');
    // will create two lines
    cy.get(`g[data-type="${CELL_TYPES.LINK}"]`).should('have.length', 2);

    // topic creation is a heavy request...we need to wait util ready
    cy.wait(5000);

    cy.log(`auto generate topic for ftp source -> stream1`);
    cy.getCell(elements.ftpSourceName).trigger('mouseover');
    cy.cellAction(elements.ftpSourceName, ACTIONS.link).click();
    cy.getCell(elements.streamName1).click();

    // generate "T2" pipeline-only topic
    cy.findAllByText('T2').should('exist');
    // will create two lines (total four lines)
    cy.get(`g[data-type="${CELL_TYPES.LINK}"]`).should('have.length', 4);

    // topic creation is a heavy request...we need to wait util ready
    cy.wait(5000);

    cy.log(`auto generate topic for stream2 -> stream3`);
    cy.getCell(elements.streamName2).trigger('mouseover');
    cy.cellAction(elements.streamName2, ACTIONS.link).click();
    cy.getCell(elements.streamName3).click();

    // generate "T3" pipeline-only topic
    cy.findAllByText('T3').should('exist');
    // will create two lines (total six lines)
    cy.get(`g[data-type="${CELL_TYPES.LINK}"]`).should('have.length', 6);

    // topic creation is a heavy request...we need to wait util ready
    cy.wait(5000);

    // check pipeline-only topics will be shown in topic list
    cy.contains('button', /workspace/i)
      .should('exist')
      .click();

    cy.findByText(/^topics$/i)
      .should('exist')
      .click();

    cy.contains('td', 'T1').should('exist');
    cy.contains('td', 'T2').should('exist');
    cy.contains('td', 'T3').should('exist');

    cy.findAllByText(/pipeline only/i)
      .filter(':visible')
      .should('have.length', 3);
  });
});
