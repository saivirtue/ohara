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

import { get } from 'lodash';

import * as useApi from 'components/controller';
import { useRef } from 'react';

const useCreateServices = url => {
  const { getData, postApi } = useApi.usePostApi(url);
  const { putApi } = useApi.usePutApi(url);
  const { waitApi, getFinish } = useApi.useWaitApi();
  const fail = useRef();
  const createServices = async params => {
    const { postParams, checkResult } = params;
    await postApi(postParams);
    const name = get(getData(), 'data.result.name');
    if (!name) {
      fail.current = true;
      return;
    }
    await putApi(`/${name}/start`);
    await waitApi({
      url: `${url}/${name}`,
      checkFn: checkResult,
    });
    if (!getFinish()) {
      fail.current = true;
      return;
    }
  };
  const create = async params => {
    await createServices(params);
  };

  const handleFail = () => fail.current;

  return { create, handleFail };
};
export default useCreateServices;
