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

import { get, isUndefined } from 'lodash';
import * as useApi from 'components/controller';
import * as URL from 'components/controller/url';

const usePlugin = () => {
  const { getData: pipelineRes, getApi: getPipeline } = useApi.useGetApi(
    URL.PIPELINE_URL,
  );
  const { putApi: putConnector, getData: getConnector } = useApi.usePutApi(
    URL.CONNECTOR_URL,
  );
  const { waitApi, getFinish } = useApi.useWaitApi();
  const { putApi: putWorker, getData: getWorker } = useApi.usePutApi(
    URL.WORKER_URL,
  );

  const getRunningConnectors = async worker => {
    await getPipeline();
    const res = get(pipelineRes(), 'data.result', []);

    //Get the connector in execution
    const runningConnector = res
      .filter(
        pipeline => pipeline.tags.workerClusterName === worker.settings.name,
      )
      .map(pipeline => pipeline.objects)
      .reduce((start, next) => start.concat(next), [])
      .filter(
        object =>
          object.state === 'RUNNING' &&
          object.kind !== 'stream' &&
          object.kind !== 'topic',
      );
    return runningConnector;
  };

  const stopConnectors = async runningConnector => {
    let stopConnectorsSuccess = true;

    const stopConnectorCheckFn = res => {
      return isUndefined(get(res, 'data.result.state', undefined));
    };

    /* eslint-disable no-unused-vars */
    for (let connector of runningConnector) {
      await putConnector(`/${connector.name}/stop?group=${connector.group}`);
      const isSuccess = get(getConnector(), 'data.isSuccess', false);
      stopConnectorsSuccess = isSuccess ? true : false;
      if (!stopConnectorsSuccess) {
        return stopConnectorsSuccess;
      }
    }

    /* eslint-disable no-unused-vars */
    for (let stopConnector of runningConnector) {
      const stopConnectorParams = {
        url: `${URL.CONNECTOR_URL}/${stopConnector.name}?group=${stopConnector.group}`,
        checkFn: stopConnectorCheckFn,
        sleep: 3000,
      };

      await waitApi(stopConnectorParams);
      const isSuccess = getFinish();
      stopConnectorsSuccess = isSuccess ? true : false;
    }
    return stopConnectorsSuccess;
  };

  const startConnectors = async connectors => {
    let startConnectorsSuccess = true;

    const startConnectorCheckFn = res => {
      return get(res, 'data.result.state', undefined) === 'RUNNING';
    };

    /* eslint-disable no-unused-vars */
    for (let connector of connectors) {
      await putConnector(`/${connector.name}/start?group=${connector.group}`);
      const isSuccess = get(getConnector(), 'data.isSuccess', false);
      startConnectorsSuccess = isSuccess ? true : false;
      if (!startConnectorsSuccess) {
        return startConnectorsSuccess;
      }
    }

    /* eslint-disable no-unused-vars */
    for (let startConnector of connectors) {
      const startConnectorParams = {
        url: `${URL.CONNECTOR_URL}/${startConnector.name}?group=${startConnector.group}`,
        checkFn: startConnectorCheckFn,
        sleep: 3000,
      };

      await waitApi(startConnectorParams);
      const isSuccess = getFinish();
      startConnectorsSuccess = isSuccess ? true : false;
    }
    return startConnectorsSuccess;
  };

  const stopWorker = async worker => {
    const stopWorkerCheckFn = res => {
      return isUndefined(get(res, 'data.result.state', undefined));
    };

    await putWorker(`/${worker.settings.name}/stop`);
    const stopIsSuccess = get(getWorker(), 'data.isSuccess', false);
    if (!stopIsSuccess) {
      return stopIsSuccess;
    }

    const stopWorkerParams = {
      url: `${URL.WORKER_URL}/${worker.settings.name}`,
      checkFn: stopWorkerCheckFn,
      sleep: 3000,
    };

    await waitApi(stopWorkerParams);
    const isSuccess = getFinish();
    return isSuccess;
  };

  const startWorker = async worker => {
    const startWorkerCheckFn = res => {
      return get(res, 'data.result.connectors', []).length > 0;
    };

    await putWorker(`/${worker.settings.name}/start`);

    const startIsSuccess = get(getWorker(), 'data.isSuccess', false);
    if (!startIsSuccess) {
      return startIsSuccess;
    }

    const startWorkerParams = {
      url: `${URL.WORKER_URL}/${worker.settings.name}`,
      checkFn: startWorkerCheckFn,
      sleep: 3000,
    };

    await waitApi(startWorkerParams);
    const success = getFinish();
    return success;
  };

  const updateJarInfos = async props => {
    const { worker, jarInfos } = props;
    await putWorker(`/${worker.settings.name}`, {
      jarKeys: jarInfos.map(jarInfo => {
        return { name: jarInfo.name, group: jarInfo.group };
      }),
    });
  };

  return {
    getRunningConnectors,
    stopConnectors,
    startConnectors,
    stopWorker,
    startWorker,
    updateJarInfos,
  };
};

export default usePlugin;
