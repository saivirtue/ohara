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
import { cleanup, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom/extend-expect';

import * as generate from 'utils/generate';
import Plugins from '../Plugins/Plugins';
import { renderWithProvider } from 'utils/testUtils';
import * as useApi from 'components/controller';

jest.mock('components/controller');

afterEach(cleanup);

const workerName = generate.serviceName();
const pluginName = generate.serviceName();
const file = {
  group: workerName,
  lastModified: 1568704840000,
  name: pluginName,
  size: 3928198,
  tags: { type: 'plugin' },
};

const props = {
  worker: {
    settings: {
      name: workerName,
      jarInfos: [file],
    },
  },
  workerRefetch: jest.fn(),
};

describe('<Plugins />', () => {
  beforeEach(() => {
    jest.spyOn(useApi, 'useDeleteApi').mockImplementation(() => {
      return {
        getData: jest.fn(),
      };
    });

    jest.spyOn(useApi, 'useGetApi').mockImplementation(() => {
      return {
        getData: jest.fn(),
      };
    });

    jest.spyOn(useApi, 'useDeleteApi').mockImplementation(() => {
      return {
        getData: jest.fn(),
      };
    });

    jest.spyOn(useApi, 'useUploadApi').mockImplementation(() => {
      return {
        getData: jest.fn(),
      };
    });

    jest.spyOn(useApi, 'usePutApi').mockImplementation(() => {
      return {
        putApi: jest.fn(),
      };
    });

    jest.spyOn(useApi, 'useWaitApi').mockImplementation(() => {
      return {
        waitApi: jest.fn(),
      };
    });

    jest.spyOn(useApi, 'useFetchApi').mockImplementation(() => {
      return {
        data: {
          data: {
            result: [file],
          },
        },
        isLoading: false,
      };
    });
  });

  it('renders the page', async () => {
    await renderWithProvider(<Plugins {...props} />);
  });

  it('add new plugin with plugins page', async () => {
    const newFileName = generate.serviceName();
    const newFile = {
      group: workerName,
      lastModified: 1568704840000,
      name: newFileName,
      size: 3928198,
      tags: { type: 'plugin' },
    };

    jest.spyOn(useApi, 'useFetchApi').mockImplementation(() => {
      return {
        data: {
          data: {
            result: [file, newFile],
          },
        },
        isLoading: false,
      };
    });

    const { getByText } = await renderWithProvider(<Plugins {...props} />);
    getByText(
      'You’ve made some changes to the plugins: 1 added. Please restart for these settings to take effect!!',
    );
    getByText('DISCARD');
    getByText('RESTART');
    getByText(newFileName);
  });

  it('remove plugin with plugins page', async () => {
    const { getByText, getByTestId } = await renderWithProvider(
      <Plugins {...props} />,
    );
    fireEvent.click(getByTestId(pluginName));
    getByText(
      'You’ve made some changes to the plugins: 1 removed. Please restart for these settings to take effect!!',
    );
    getByText('DISCARD');
    getByText('RESTART');
    getByTestId(pluginName);
  });
});
