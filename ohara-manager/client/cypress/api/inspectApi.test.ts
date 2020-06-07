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
import { KIND, MODE } from '../../src/const';
import * as generate from '../../src/utils/generate';
import * as inspect from '../../src/api/inspectApi';
import { InspectServiceResponse } from '../../src/api/apiInterface/inspectInterface';
import { createServicesInNodes, deleteAllServices } from '../utils';

describe('Inspect API', () => {
  it('fetchConfiguratorInfo', async () => {
    const infoRes = await inspect.getConfiguratorInfo();

    const { mode, versionInfo } = infoRes.data;
    const { branch, version, user, revision, date } = versionInfo;

    // we're using fake configurator in our tests; so this value should be "FAKE"
    expect(mode).to.be.a('string');
    expect(mode).to.eq(MODE.FAKE);

    expect(branch).to.be.a('string');

    expect(version).to.be.a('string');

    expect(revision).to.be.a('string');

    expect(user).to.be.a('string');

    expect(date).to.be.a('string');
  });

  // Note this API is a special route from manager, not configurator
  // it depends on a gradle task :ohara-common:versionFile to generate version
  // info when running in dev mode
  it('fetchManagerInfo', async () => {
    const result = await inspect.getManagerInfo();

    const { branch, version, user, revision, date } = result.data;

    expect(branch).to.be.a('string');
    expect(version).to.be.a('string');
    expect(revision).to.be.a('string');
    expect(user).to.be.a('string');
    expect(date).to.be.a('string');
  });

  it('fetchServiceDefinition', async () => {
    function expectResult(
      serviceName: inspect.INSPECT_KIND,
      result: InspectServiceResponse,
    ) {
      const { imageName, settingDefinitions, classInfos } = result.data;
      expect(imageName).to.be.a('string');
      expect(imageName).to.include(serviceName);

      expect(settingDefinitions).to.be.an('array');
      expect(settingDefinitions.length > 0).to.be.true;

      expect(classInfos).to.be.an('array');
    }

    const infoZookeeper = await inspect.getZookeeperInfo();
    expectResult(inspect.INSPECT_KIND.zookeeper, infoZookeeper);

    const infoBroker = await inspect.getBrokerInfo();
    expectResult(inspect.INSPECT_KIND.broker, infoBroker);

    const infoWorker = await inspect.getWorkerInfo();
    expectResult(inspect.INSPECT_KIND.worker, infoWorker);

    const infoStream = await inspect.getStreamsInfo();
    expectResult(inspect.INSPECT_KIND.stream, infoStream);

    const infoShabondi = await inspect.getShabondiInfo();
    expectResult(inspect.INSPECT_KIND.shabondi, infoShabondi);
  });

  it('fetchServiceDefinitionByName', async () => {
    const { zookeeper, broker, worker } = await createServicesInNodes({
      withWorker: true,
      withBroker: true,
      withZookeeper: true,
    });

    function expectResult(
      serviceName: inspect.INSPECT_KIND,
      result: InspectServiceResponse,
    ) {
      const { imageName, settingDefinitions, classInfos } = result.data;
      expect(imageName).to.be.a('string');
      expect(imageName).to.include(serviceName);

      expect(settingDefinitions).to.be.an('array');
      expect(settingDefinitions.length > 0).to.be.true;

      expect(classInfos).to.be.an('array');
    }

    const infoZookeeper = await inspect.getZookeeperInfo(zookeeper);
    expectResult(inspect.INSPECT_KIND.zookeeper, infoZookeeper);

    const infoBroker = await inspect.getBrokerInfo(broker);
    expectResult(inspect.INSPECT_KIND.broker, infoBroker);

    const infoWorker = await inspect.getWorkerInfo(worker);
    expectResult(inspect.INSPECT_KIND.worker, infoWorker);
  });

  it('fetchTopicDefinition', async () => {
    const infoTopic = await inspect.getBrokerInfo();
    const { imageName, settingDefinitions, classInfos } = infoTopic.data;

    expect(imageName).to.be.a('string');

    expect(settingDefinitions).to.be.an('array');

    expect(classInfos).to.be.an('array');
    classInfos.forEach((classInfo) => {
      const { className, classType, settingDefinitions } = classInfo;

      expect(className).to.be.a('string');

      expect(classType).to.be.a('string');
      expect(classType).to.eq(KIND.topic);

      expect(settingDefinitions).to.be.an('array');
      expect(settingDefinitions.length > 0).to.be.true;
    });
  });

  it('fetchConnectorDefinition', async () => {
    await deleteAllServices();
    const { worker } = await createServicesInNodes({
      withWorker: true,
      withBroker: true,
      withZookeeper: true,
    });

    const infoWorker = await inspect.getWorkerInfo({
      name: worker.name,
      group: worker.group,
    });

    expect(infoWorker.data.classInfos).to.be.an('array');
    infoWorker.data.classInfos.forEach((classInfo) => {
      const { className, classType, settingDefinitions } = classInfo;
      expect(className).to.be.a('string');

      expect(classType).to.be.a('string');
      // TODO: a hot fix to unit test
      expect([KIND.source, KIND.sink, 'partitioner']).to.include(classType);

      expect(settingDefinitions).to.be.an('array');
      expect(settingDefinitions.length > 0).to.be.true;
    });
  });

  it('fetchStreamFileDefinition', () => {
    const file: Cypress.FixtureRequest = {
      fixturePath: 'stream',
      name: 'ohara-it-stream.jar',
      group: generate.serviceName({ prefix: 'group' }),
    };
    file.tags = { name: file.name };

    cy.createJar(file)
      .then((params) => inspect.getFileInfoWithoutUpload(params))
      .then((result) => {
        const {
          name,
          group,
          classInfos,
          lastModified,
          size,
          tags,
          url,
        } = result.data;

        expect(name).to.be.a('string');
        expect(name).to.eq(file.name);

        expect(group).to.be.a('string');
        expect(group).to.eq(file.group);

        expect(classInfos).to.be.an('array');
        // the ohara-it-stream.jar only includes stream class, so the length must be 1
        expect(classInfos).have.lengthOf(1);

        const { className, classType, settingDefinitions } = classInfos[0];
        expect(className).to.be.a('string');

        expect(classType).to.be.a('string');
        expect(classType).to.eq(KIND.stream);

        expect(settingDefinitions).to.be.an('array');
        expect(settingDefinitions.length > 0).to.be.true;

        expect(lastModified).to.be.a('number');

        expect(size).to.be.a('number');
        expect(size > 0).to.be.true;

        expect(tags).to.be.an('object');
        expect(tags.name).to.eq(file.name);

        // the file is not uploaded so url is not generated
        expect(url).to.be.undefined;
      });
  });

  it('fetchSourceConnectorFileDefinition', () => {
    const source = {
      fixturePath: 'plugin',
      // we use an existing file to simulate upload jar
      name: 'ohara-it-source.jar',
      group: generate.serviceName({ prefix: 'group' }),
      tags: { name: 'ohara-it-source.jar' },
    };

    cy.createJar(source)
      .then((params) => inspect.getFileInfoWithoutUpload(params))
      .then((result) => {
        const {
          name,
          group,
          classInfos,
          lastModified,
          size,
          tags,
          url,
        } = result.data;

        expect(name).to.be.a('string');
        expect(name).to.eq(source.name);

        expect(group).to.be.a('string');
        expect(group).to.eq(source.group);

        expect(classInfos).to.be.an('array');
        // the ohara-it-stream.jar only includes stream class, so the length must be 1
        expect(classInfos).have.lengthOf(1);

        const { className, classType, settingDefinitions } = classInfos[0];
        expect(className).to.be.a('string');

        expect(classType).to.be.a('string');
        expect(classType).to.eq(KIND.source);

        expect(settingDefinitions).to.be.an('array');
        expect(settingDefinitions.length > 0).to.be.true;

        expect(lastModified).to.be.a('number');

        expect(size).to.be.a('number');
        expect(size > 0).to.be.true;

        expect(tags).to.be.an('object');
        expect(tags.name).to.eq(source.name);

        // the file is not uploaded so url is not generated
        expect(url).to.be.undefined;
      });
  });

  it('fetchSinkConnectorFileDefinition', () => {
    const sink = {
      fixturePath: 'plugin',
      // we use an existing file to simulate upload jar
      name: 'ohara-it-sink.jar',
      group: generate.serviceName({ prefix: 'group' }),
      tags: { name: 'ohara-it-sink.jar' },
    };

    cy.createJar(sink)
      .then((params) => inspect.getFileInfoWithoutUpload(params))
      .then((result) => {
        const {
          name,
          group,
          classInfos,
          lastModified,
          size,
          tags,
          url,
        } = result.data;

        expect(name).to.be.a('string');
        expect(name).to.eq(sink.name);

        expect(group).to.be.a('string');
        expect(group).to.eq(sink.group);

        expect(classInfos).to.be.an('array');
        // the ohara-it-stream.jar only includes stream class, so the length must be 1
        expect(classInfos).have.lengthOf(1);

        const { className, classType, settingDefinitions } = classInfos[0];
        expect(className).to.be.a('string');

        expect(classType).to.be.a('string');
        expect(classType).to.eq(KIND.sink);

        expect(settingDefinitions).to.be.an('array');
        expect(settingDefinitions.length > 0).to.be.true;

        expect(lastModified).to.be.a('number');

        expect(size).to.be.a('number');
        expect(size > 0).to.be.true;

        expect(tags).to.be.an('object');
        expect(tags.name).to.eq(sink.name);

        // the file is not uploaded so url is not generated
        expect(url).to.be.undefined;
      });
  });
});
