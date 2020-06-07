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
import * as nodeApi from '../../src/api/nodeApi';
import { NODE_STATE } from '../../src/api/apiInterface/nodeInterface';
import { deleteAllServices } from '../utils';

const generateNode = () => {
  const params = {
    hostname: generate.serviceName({ prefix: 'node' }),
    port: generate.port(),
    user: generate.userName(),
    password: generate.password(),
    tags: {},
  };
  return params;
};

describe('Node API', () => {
  beforeEach(() => deleteAllServices());

  it('createNode', async () => {
    const params = generateNode();

    const result = await nodeApi.create(params);

    const {
      hostname,
      port,
      user,
      password,
      lastModified,
      tags,
      services,
      state,
      resources,
    } = result.data;

    expect(hostname).to.be.a('string');
    expect(hostname).to.eq(params.hostname);

    expect(port).to.be.a('number');
    expect(port).to.eq(params.port);

    expect(user).to.be.a('string');
    expect(user).to.eq(params.user);

    expect(password).to.be.a('string');
    expect(password).to.eq(params.password);

    expect(services).to.be.an('array');

    expect(state).to.be.a('string');
    expect(state).to.eq(NODE_STATE.AVAILABLE);

    expect(resources).to.be.an('array');

    expect(lastModified).to.be.a('number');

    expect(tags).to.be.an('object');

    services.forEach((service) => {
      expect(service.name).to.be.a('string');
      expect(service.clusterKeys).to.be.an('array');
    });
  });

  it('updateNode', async () => {
    const params = generateNode();
    await nodeApi.create(params);

    let newParams = Object.assign({}, params);
    newParams.user = generate.userName();
    newParams.password = generate.password();
    newParams.tags = { a: 'tag' };

    const result = await nodeApi.update(newParams);

    const {
      hostname,
      port,
      user,
      password,
      lastModified,
      tags,
      services,
    } = result.data;

    expect(hostname).to.be.a('string');
    expect(hostname).to.eq(params.hostname);

    expect(port).to.be.a('number');
    expect(port).to.eq(params.port);

    expect(services).to.be.an('array');

    expect(lastModified).to.be.a('number');

    expect(tags).to.be.an('object');
    expect(tags.a).to.eq('tag');

    // we update the user and password with newParams
    expect(user).to.be.a('string');
    expect(user).to.eq(newParams.user);

    expect(password).to.be.a('string');
    expect(password).to.eq(newParams.password);
  });

  it('fetchNode', async () => {
    const params = generateNode();
    await nodeApi.create(params);

    const result = await nodeApi.get(params.hostname);

    const {
      hostname,
      port,
      user,
      password,
      lastModified,
      tags,
      services,
      state,
      resources,
    } = result.data;

    expect(hostname).to.be.a('string');
    expect(hostname).to.eq(params.hostname);

    expect(port).to.be.a('number');
    expect(port).to.eq(params.port);

    expect(user).to.be.a('string');
    expect(user).to.eq(params.user);

    expect(password).to.be.a('string');
    expect(password).to.eq(params.password);

    expect(services).to.be.an('array');

    expect(state).to.be.a('string');
    expect(state).to.eq(NODE_STATE.AVAILABLE);

    expect(resources).to.be.an('array');

    expect(lastModified).to.be.a('number');

    expect(tags).to.be.an('object');
  });

  it('fetchNodes', async () => {
    const paramsOne = generateNode();
    const paramsTwo = generateNode();

    await nodeApi.create(paramsOne);
    await nodeApi.create(paramsTwo);

    const result = await nodeApi.getAll();

    expect(result.data).to.be.an('array');
    const nodes = result.data.filter(
      (node) =>
        node.hostname === paramsOne.hostname ||
        node.hostname === paramsTwo.hostname,
    );

    expect(nodes.length).to.eq(2);
  });

  it('deleteNode', async () => {
    const params = generateNode();
    await nodeApi.create(params);
    await nodeApi.remove(params.hostname);

    const result = await nodeApi.getAll();

    const nodes = result.data.filter(
      (node) => node.hostname === params.hostname,
    );

    expect(nodes.length).to.eq(0);
  });
});
