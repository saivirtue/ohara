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

import faker from 'faker';

// We need to use the relative path here, or cypress won't
// be able to resolve the path
import { CONNECTOR_TYPES } from '../constants/pipelines';

const { system, random, lorem, internet, date } = faker;
const { fileName: name } = system;
const { uuid: id, number, alphaNumeric: revision } = random;
const { paragraph: message, word } = lorem;
const { domainName, ip, userName, url, password } = internet;

export const connectors = () => {
  const connectors = Object.values(CONNECTOR_TYPES).map(type => {
    return {
      className: type,

      // empty array for now as I don't really see the need for these valus
      definitions: [
        {
          defaultValue: name(),
          key: 'kind',
        },
        {
          defaultValue: name(),
          key: 'version',
        },
        {
          defaultValue: name(),
          key: 'author',
        },
      ],
    };
  });

  return connectors;
};

export const topics = ({
  count = 1,
  brokerClusterName = serviceName(),
  workspaceName,
} = {}) => {
  let topics = [];

  while (count > 0) {
    count--;

    const topic = {
      name: serviceName(),
      lastModified: date.past(),
      metrics: {},
      numberOfPartitions: number(),
      numberOfReplications: number(),
      brokerClusterName,
      group: workspaceName,
    };

    topics.push(topic);
  }

  return topics;
};

export const streamApps = ({
  count = 1,
  workspaceName = serviceName(),
} = {}) => {
  let streamApps = [];

  while (count > 0) {
    count--;

    const streamApp = {
      group: workspaceName,
      lastModified: date.past(),
      name: serviceName(),
      size: number(),
      url: url(),
      tags: { type: 'streamjar' },
    };

    streamApps.push(streamApp);
  }

  return streamApps;
};

export const nodes = ({ count = 1, overrides = {} } = {}) => {
  let nodes = [];

  while (count > 0) {
    count--;

    const node = {
      lastModified: date.past(),
      nodeNames: [serviceName()],
      name: serviceName(),
      port: port(),
      ...overrides,
    };

    nodes.push(node);
  }

  return nodes;
};

export const broker = ({ nodeCount = 1, overrides = {} } = {}) => {
  let nodeNames = [];

  while (nodeCount > 0) {
    nodeCount--;
    const nodeName = serviceName();

    nodeNames.push(nodeName);
  }
  const broker = {
    settings: {
      clientPort: port(),
      imageName: name(),
      jmxPort: port(),
      name: serviceName(),
      nodeNames,
      zookeeperClusterName: serviceName(),
      ...overrides,
    },
  };

  return broker;
};

export const zookeeper = ({ nodeCount = 1, overrides = {} } = {}) => {
  let nodeNames = [];

  while (nodeCount > 0) {
    nodeCount--;
    const nodeName = serviceName();

    nodeNames.push(nodeName);
  }
  const zookeeper = {
    settings: {
      clientPort: port(),
      imageName: name(),
      name: serviceName(),
      nodeNames,
      ...overrides,
    },
  };

  return zookeeper;
};

export const workers = ({ count = 1, overrides } = {}) => {
  let workers = [];

  while (count > 0) {
    count--;

    const worker = {
      settings: {
        nodeNames: [name(), name()],
        name: name(),
      },
    };

    workers.push(worker);
  }

  return workers;
};

export const columnRows = (rowCount = 1) => {
  let columnRows = [];

  while (rowCount > 0) {
    const columnRow = {
      columnName: name(),
      newColumnName: name(),
      currType: 'String',
      order: rowCount,
    };

    rowCount--;
    columnRows.push(columnRow);
  }

  return columnRows;
};

export const singleGraph = (overrides = {}) => {
  let kind;
  let className;

  const getKindByClass = className => {
    const includes = str => className.includes(str);

    if (includes('source')) {
      return 'source';
    } else if (includes('sink')) {
      return 'sink';
    } else if (includes('streamApp')) {
      return 'streamApp';
    } else if (includes('topic')) {
      return 'topic';
    }
  };

  if (!overrides.className) {
    const availClass = Object.values(CONNECTOR_TYPES);
    className = availClass[Math.floor(Math.random() * availClass.length)];
    kind = getKindByClass(className);
  }

  return {
    className,
    kind,
    id: id(),
    lastModified: number(),
    metrics: {},
    name: `Untitled ${kind}`,
    to: [],
    ...overrides,
  };
};

export const port = ({ min = 5000, max = 65535 } = {}) => {
  return Math.floor(Math.random() * (max - min + 1)) + min;
};

export const serviceName = ({ length = 10, prefix } = {}) => {
  let name = '';
  const possible = 'abcdefghijklmnopqrstuvwxyz0123456789';

  for (let i = 0; i < length; i++) {
    name += possible.charAt(Math.floor(Math.random() * possible.length));
  }

  if (prefix) return `${prefix}${name}`;

  return name;
};

export const serverHost = () => {
  return 'http://' + window.location.hostname;
};

export {
  name,
  id,
  message,
  domainName,
  ip,
  userName,
  number,
  url,
  password,
  word,
  revision,
  date,
};
