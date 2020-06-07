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
import * as objectApi from '../../src/api/objectApi';

const generateObject = () => {
  const params = {
    name: generate.serviceName({ prefix: 'object' }),
    group: generate.serviceName({ prefix: 'group' }),
    tags: {
      domain: generate.domainName(),
      id: generate.number(),
      desc: generate.message(),
    },
  };
  return params;
};

describe('Object API', () => {
  it('createObject', async () => {
    const params = generateObject();

    const result = await objectApi.create(params);

    const { name, group, tags, lastModified } = result.data;

    expect(name).to.be.a('string');
    expect(name).to.eq(params.name);

    expect(group).to.be.a('string');
    expect(group).to.eq(params.group);

    expect(tags).to.be.an('object');
    expect(tags.domain).to.eq(params.tags.domain);
    expect(tags.id).to.eq(params.tags.id);
    expect(tags.desc).to.eq(params.tags.desc);

    expect(lastModified).to.be.a('number');
  });

  it('updateObject', async () => {
    const params = generateObject();
    await objectApi.create(params);

    let newParams = { ...params, pwd: generate.password() };
    newParams.tags.domain = generate.domainName(10);

    const result = await objectApi.update(newParams);

    const { name, group, tags, lastModified, pwd } = result.data;

    expect(name).to.be.a('string');
    expect(name).to.eq(params.name);

    expect(group).to.be.a('string');
    expect(group).to.eq(params.group);

    expect(tags).to.be.an('object');
    expect(tags.domain).to.eq(newParams.tags.domain);
    expect(tags.id).to.eq(newParams.tags.id);
    expect(tags.desc).to.eq(newParams.tags.desc);
    expect(pwd).to.eq(newParams.pwd);

    expect(lastModified).to.be.a('number');
  });

  it('fetchObject', async () => {
    const params = generateObject();
    await objectApi.create(params);

    const result = await objectApi.get(params);

    const { name, group, tags, lastModified } = result.data;

    expect(name).to.be.a('string');
    expect(name).to.eq(params.name);

    expect(group).to.be.a('string');
    expect(group).to.eq(params.group);

    expect(tags).to.be.an('object');
    expect(tags.domain).to.eq(params.tags.domain);
    expect(tags.id).to.eq(params.tags.id);
    expect(tags.desc).to.eq(params.tags.desc);

    expect(lastModified).to.be.a('number');
  });

  it('fetchObjects', async () => {
    const paramsOne = generateObject();
    const paramsTwo = generateObject();

    await objectApi.create(paramsOne);
    await objectApi.create(paramsTwo);

    const result = await objectApi.getAll();

    const objects = result.data.filter(
      (object) =>
        (object.name === paramsOne.name && object.group === paramsOne.group) ||
        (object.name === paramsTwo.name && object.group === paramsTwo.group),
    );

    expect(objects.length).to.eq(2);
  });

  it('deleteObject', async () => {
    const params = generateObject();
    await objectApi.create(params);
    await objectApi.remove(params);

    const result = await objectApi.getAll();

    const objects = result.data.filter(
      (object) => object.name === params.name && object.group === params.group,
    );

    expect(objects.length).to.eq(0);
  });
});
