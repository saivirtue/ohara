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

// Note: Do not change the usage of absolute path
// unless you have a solution to resolve TypeScript + Coverage
import * as generate from '../../src/utils/generate';
import * as topicApi from '../../src/api/topicApi';
import * as validateApi from '../../src/api/validateApi';
import { SOURCES } from '../../src/api/apiInterface/connectorInterface';
import { createServicesInNodes, deleteAllServices } from '../utils';

const generateValidation = async () => {
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
  await topicApi.start(topic);

  const connector = {
    name: generate.serviceName({ prefix: 'connector' }),
    group: generate.serviceName({ prefix: 'group' }),
    connector__class: SOURCES.perf,
    topicKeys: [{ name: topic.name, group: topic.group }],
    workerClusterKey: {
      name: worker.name,
      group: worker.group,
    },
  };

  const validation = {
    uri: generate.url(),
    url: generate.url(),
    user: generate.userName(),
    password: generate.password(),
    hostname: generate.domainName(),
    port: generate.port(),
    workerClusterKey: {
      name: worker.name,
      group: worker.group,
    },
  };
  return { connector, validation };
};

describe('Validate API', () => {
  beforeEach(() => deleteAllServices());

  it('validateConnector', async () => {
    const { connector } = await generateValidation();
    const result = await validateApi.validateConnector(connector);

    const { errorCount, settings } = result.data;

    expect(errorCount).to.eq(0);

    expect(settings.length > 0).to.be.true;

    settings.forEach((report) => {
      const { definition, value } = report;
      expect(definition).to.be.an('object');

      expect(value).to.be.an('object');
      expect(value.errors).to.be.an('array');
      expect(value.errors).have.lengthOf(0);
    });
  });
});
