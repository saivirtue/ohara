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
import { KIND } from '../../src/const';
import * as generate from '../../src/utils/generate';
import * as fileApi from '../../src/api/fileApi';
import { deleteAllServices } from '../utils';

const generateFile = () => {
  const params: Cypress.FixtureRequest = {
    fixturePath: 'plugin',
    // we use an existing file to simulate upload jar
    name: 'ohara-it-source.jar',
    group: generate.serviceName({ prefix: 'group' }),
  };
  params.tags = { name: params.name };
  return params;
};

describe('File API', () => {
  beforeEach(() => deleteAllServices());

  it('uploadJar', () => {
    const file = generateFile();
    cy.createJar(file)
      .then((params) => fileApi.create(params))
      .then((result) => {
        const {
          group,
          name,
          size,
          url,
          lastModified,
          classInfos,
          tags,
        } = result.data;
        expect(group).to.be.a('string');
        expect(group).to.eq(file.group);

        expect(name).to.be.a('string');
        expect(name).to.eq(file.name);

        expect(size).to.be.a('number');
        expect(size).to.not.eq(0);

        expect(url).to.be.a('string');
        expect(url?.length).to.not.eq(0);

        expect(lastModified).to.be.a('number');

        expect(classInfos).to.be.an('array');
        expect(classInfos.length > 0).to.be.true;

        const { className, classType, settingDefinitions } = classInfos[0];
        expect(className).to.be.a('string');

        expect(classType).to.be.a('string');
        expect(classType).to.eq(KIND.source);

        expect(settingDefinitions).to.be.an('array');
        expect(settingDefinitions.length > 0).to.be.true;

        expect(tags).to.be.an('object');
        expect(tags.name).to.eq(file.name);
      });
  });

  it('fetchJars', () => {
    const file = generateFile();
    cy.createJar(file)
      .then((params) => fileApi.create(params))
      .then(() => {
        fileApi.getAll({ group: file.group }).then((result) => {
          expect(result.data).to.be.an('array');
          expect(result.data.length).to.eq(1);

          const fileInfo = result.data[0];
          expect(fileInfo).to.include.keys('name', 'group');
          expect(fileInfo.name).to.be.a('string');
          expect(fileInfo.group).to.be.a('string');
          expect(fileInfo.tags.name).to.eq(fileInfo.name);
        });
      });
  });

  it('fetchJar', () => {
    const file = generateFile();
    cy.createJar(file)
      .then((params) => fileApi.create(params))
      .then(() => {
        fileApi.get(file).then((result) => {
          const {
            group,
            name,
            size,
            url,
            lastModified,
            classInfos,
            tags,
          } = result.data;
          expect(group).to.be.a('string');
          expect(group).to.eq(file.group);

          expect(name).to.be.a('string');
          expect(name).to.eq(file.name);

          expect(size).to.be.a('number');
          expect(size).to.not.eq(0);

          expect(url).to.be.a('string');
          expect(url?.length).to.not.eq(0);

          expect(classInfos).to.be.an('array');
          expect(classInfos.length > 0).to.be.true;

          const { className, classType, settingDefinitions } = classInfos[0];
          expect(className).to.be.a('string');

          expect(classType).to.be.a('string');
          expect(classType).to.eq(KIND.source);

          expect(settingDefinitions).to.be.an('array');
          expect(settingDefinitions.length > 0).to.be.true;

          expect(lastModified).to.be.a('number');

          expect(tags).to.be.an('object');
          expect(tags.name).to.eq(file.name);
        });
      });
  });

  it('updateJarTags', () => {
    const file = generateFile();
    const newFile = Object.assign({}, file, { tags: { tag: 'aaa' } });

    cy.createJar(file)
      .then((params) => fileApi.create(params))
      .then(() => {
        fileApi.update(newFile).then((result) => {
          const { group, name, size, url, lastModified, tags } = result.data;
          expect(group).to.be.a('string');
          expect(group).to.eq(file.group);

          expect(name).to.be.a('string');
          expect(name).to.eq(file.name);

          expect(size).to.be.a('number');
          expect(size).to.not.eq(0);

          expect(url).to.be.a('string');
          expect(url?.length).to.not.eq(0);

          expect(lastModified).to.be.a('number');

          expect(tags).to.be.an('object');
          expect(tags.tag).to.eq('aaa');
        });
      });
  });

  it('deleteJar', () => {
    const file = generateFile();
    cy.createJar(file)
      .then((params) => fileApi.create(params))
      .then(() => fileApi.remove(file))
      .then(() => fileApi.getAll())
      .then((result) => {
        expect(result.data.length).to.eq(0);
      });
  });
});
