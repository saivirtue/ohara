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

import React, { useState, useEffect, useRef } from 'react';
import PropTypes from 'prop-types';
import Tooltip from '@material-ui/core/Tooltip';
import IconButton from '@material-ui/core/IconButton';
import { get, divide, floor } from 'lodash';

import * as MESSAGES from 'constants/messages';
import * as commonUtils from 'utils/commonUtils';
import * as utils from '../WorkspacesDetailPageUtils';
import { Progress } from 'components/common/Mui/Feedback';
import { Button } from 'components/common/Mui/Form';
import { DeleteDialog } from 'components/common/Mui/Dialog';
import { SortTable } from 'components/common/Mui/Table';
import { Main, ActionIcon, StyledInputFile, StyledLabel } from './styles';
import * as useApi from 'components/controller';
import * as URL from 'components/controller/url';
import usePlugin from './usePlugin';
import useSnackbar from 'components/context/Snackbar/useSnackbar';

const Plugins = props => {
  const { worker, workerRefetch } = props;
  const [jarNameToBeDeleted, setJarNameToBeDeleted] = useState('');
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [activeStep, setActiveStep] = useState(0);
  const [isResetModalOpen, setIsResetModalOpen] = useState(false);
  const deleteType = useRef(false);
  const {
    data: jarsRes,
    isLoading: jarIsLoading,
    refetch: jarRefetch,
  } = useApi.useFetchApi(URL.FILE_URL);
  const jars = get(jarsRes, 'data.result', []).filter(
    jar => jar.tags.type === 'plugin',
  );

  const [workerActions, setWorkerActions] = useState([]);

  const { getData: getJarRes, uploadApi } = useApi.useUploadApi(URL.FILE_URL);
  const { getData: getDeleteRes, deleteApi } = useApi.useDeleteApi(
    URL.FILE_URL,
  );
  const { showMessage } = useSnackbar();
  const {
    getRunningConnectors,
    startConnectors,
    stopConnectors,
    startWorker,
    stopWorker,
    updateJarInfos,
  } = usePlugin();
  const [errors, setErrors] = useState([]);
  const connectors = useRef();
  const saveJarInfos = useRef();

  useEffect(() => {
    const { jarInfos } = worker.settings;
    const defaultAction = jarInfos.map(jarInfo => {
      return {
        name: jarInfo.name,
        size: jarInfo.size,
        lastModified: jarInfo.lastModified,
        group: jarInfo.group,
        type: 'READY',
      };
    });

    setWorkerActions(defaultAction);
  }, [worker]);

  const uploadJar = async file => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('group', worker.settings.name);
    formData.append('tags', '{"type":"plugin"}');
    await uploadApi(formData);

    const isSuccess = get(getJarRes(), 'data.isSuccess', false);
    if (isSuccess) {
      showMessage(MESSAGES.PLUGIN_UPLOAD_SUCCESS);
    }
    jarRefetch(true);
  };

  const handleFileSelect = event => {
    const file = event.target.files[0];

    const isDuplicate = () =>
      jars
        .filter(jar => jar.tags.type === 'plugin')
        .some(jar => jar.name === file.name);

    if (isDuplicate()) {
      showMessage('The plugin name already exists!');
      return;
    }

    if (event.target.files[0]) {
      uploadJar(file);
    }
  };

  const headRows = [
    { id: 'name', label: 'File name' },
    { id: 'size', label: 'File size(KB)' },
    { id: 'lastModified', label: 'Last modified' },
    { id: 'loaded', label: 'Loaded' },
    { id: 'action', label: 'Action', sortable: false },
  ];

  const handleModalOpen = jarName => {
    setJarNameToBeDeleted(jarName);
    setIsModalOpen(true);
  };

  const handleUndo = workerName => {
    const newAction = workerActions.map(workerAction => {
      if (workerAction.name === workerName) {
        const newType = workerAction.type === 'READY' ? 'DELETE' : 'READY';
        const undoAction = {
          name: workerAction.name,
          size: workerAction.size,
          lastModified: workerAction.lastModified,
          type: newType,
          group: workerAction.group,
        };
        return undoAction;
      } else {
        return workerAction;
      }
    });

    setWorkerActions(newAction);
  };

  const workerActionButton = data => {
    const { name } = data;
    return (
      <>
        {get(
          workerActions.filter(workerAction => workerAction.name === name)[0],
          'type',
          'READY',
        ) === 'READY' ? (
          <Tooltip
            title={`Delete ${name} form this workerspace`}
            enterDelay={1000}
          >
            <IconButton data-testid={name} onClick={() => handleUndo(name)}>
              <ActionIcon className="fas fa-trash-alt" />
            </IconButton>
          </Tooltip>
        ) : (
          <Tooltip title={`Undo delete ${name}`} enterDelay={1000}>
            <IconButton data-testid={name} onClick={() => handleUndo(name)}>
              <ActionIcon className="fas fa-undo" />
            </IconButton>
          </Tooltip>
        )}
      </>
    );
  };

  const jarActionButton = data => {
    const { name } = data;
    return (
      <Tooltip title={`Delete ${name} form file`} enterDelay={1000}>
        <IconButton data-testid={name} onClick={() => handleModalOpen(name)}>
          <ActionIcon className="fas fa-trash-alt" />
        </IconButton>
      </Tooltip>
    );
  };

  const loadedButton = action => {
    if (action) {
      return (
        <Tooltip title={`Is Loaded in workspace`} enterDelay={1000}>
          <ActionIcon className="fas fa-check" />
        </Tooltip>
      );
    } else {
      return;
    }
  };

  const workerRows = workerActions.map(jar => {
    return {
      name: jar.name,
      size: floor(divide(jar.size, 1024), 1),
      lastModified: utils.getDateFromTimestamp(jar.lastModified),
      loaded: loadedButton(true),
      action: workerActionButton(jar),
      type: jar.type,
    };
  });

  const jarRows = jars
    .filter(jar => jar.tags.type === 'plugin')
    .filter(
      jar =>
        !worker.settings.jarInfos
          .map(workerJars => workerJars.name)
          .includes(jar.name),
    )
    .map(jar => {
      return {
        name: jar.name,
        size: floor(divide(jar.size, 1024), 1),
        lastModified: utils.getDateFromTimestamp(jar.lastModified),
        loaded: loadedButton(false),
        action: jarActionButton(jar),
        type: 'ADD',
      };
    });

  const handleDelete = async () => {
    if (jarNameToBeDeleted) {
      await deleteApi(`${jarNameToBeDeleted}?group=${worker.settings.name}`);
      const isSuccess = get(getDeleteRes(), 'data.isSuccess', false);
      setDeleting(false);

      if (isSuccess) {
        showMessage(MESSAGES.STREAM_APP_DELETE_SUCCESS);
        setIsModalOpen(false);
        setJarNameToBeDeleted('');
        jarRefetch(true);
      }
    }
  };

  const resetWorkerAction = () => {
    const newAction = workerActions.map(workerAction => {
      if (workerAction.type === 'DELETE') {
        const undoAction = {
          name: workerAction.name,
          size: workerAction.size,
          lastModified: workerAction.lastModified,
          type: 'READY',
          group: workerAction.group,
        };
        return undoAction;
      } else {
        return workerAction;
      }
    });

    setWorkerActions(newAction);
  };

  const handleDiscard = async () => {
    const newJar = jars
      .filter(jar => jar.tags.type === 'plugin')
      .filter(
        jar =>
          !worker.settings.jarInfos
            .map(workerJars => workerJars.name)
            .includes(jar.name),
      );

    newJar.forEach(jar =>
      deleteApi(`${jar.name}?group=${worker.settings.name}`),
    );

    await commonUtils.sleep(500);

    jarRefetch(true);

    resetWorkerAction();
  };

  const resetModal = form => {
    deleteType.current = false;
    setIsLoading(false);
    setActiveStep(0);
    connectors.current = [];
    saveJarInfos.current = [];
    setWorkerActions([]);
    workerRefetch(true);
    jarRefetch(true);
    setErrors([]);
  };

  const handleError = async params => {
    const newError = errors.push(params);
    setErrors(newError);

    const reverts = errors.filter(error => error.success === false);
    if (reverts.length > 0) {
      deleteType.current = true;
    }
    /* eslint-disable no-unused-vars */
    for (let revert of reverts) {
      switch (revert.name) {
        case 'stopConnectors':
          setActiveStep(1);
          await startConnectors(connectors.current);
          break;

        case 'stopWorker':
          setActiveStep(2);
          await startWorker(worker);
          setActiveStep(1);
          await startConnectors(connectors.current);
          break;

        case 'startWorker':
          setActiveStep(3);
          await updateJarInfos({ worker, jarInfos: saveJarInfos.current });
          setActiveStep(2);
          await startWorker(worker);
          setActiveStep(1);
          await startConnectors(connectors.current);
          break;

        case 'startConnectors':
          await stopWorker(worker);
          setActiveStep(3);
          await updateJarInfos({ worker, jarInfos: saveJarInfos.current });
          setActiveStep(2);
          await startWorker(worker);
          setActiveStep(1);
          await startConnectors(connectors.current);
          break;

        default:
          break;
      }
    }
    if (reverts.length > 0) {
      resetModal();
    }
  };

  const handleRestart = async () => {
    setIsResetModalOpen(false);
    setIsLoading(true);

    connectors.current = await getRunningConnectors(worker);

    saveJarInfos.current = worker.settings.jarInfos;

    const stopConnectorsSuccess = await stopConnectors(connectors.current);

    handleError({ name: 'stopConnectors', success: stopConnectorsSuccess });

    if (deleteType.current) {
      return;
    }

    setActiveStep(1);

    const stopWorkerSuccess = await stopWorker(worker);

    handleError({ name: 'stopWorker', success: stopWorkerSuccess });

    if (deleteType.current) {
      return;
    }

    setActiveStep(2);

    let removeWorkerPlugin = [];
    let reserveWorkerPlugin = [];

    //Get files that users want to delete in the workspace
    const reservePlugin = workerActions
      .map(workerAction => {
        if (workerAction.type === 'DELETE') {
          removeWorkerPlugin.push(workerAction);
        } else {
          reserveWorkerPlugin.push(workerAction);
        }
        return workerAction;
      })
      .filter(workerAction => workerAction.type !== 'DELETE')
      .map(workerAction => {
        return {
          name: workerAction.name,
          group: workerAction.group,
        };
      });

    //Get files that users want to add in the workspace
    const addPlugin = jars
      .filter(
        jar =>
          !worker.settings.jarInfos
            .map(workerJars => workerJars.name)
            .includes(jar.name),
      )
      .map(jar => {
        return {
          name: jar.name,
          group: jar.group,
        };
      });

    //Update workspace
    await updateJarInfos({ worker, jarInfos: reservePlugin.concat(addPlugin) });

    const startWorkerSuccess = await startWorker(worker);

    handleError({ name: 'startWorker', success: startWorkerSuccess });

    if (deleteType.current) {
      return;
    }

    setActiveStep(3);

    //Waiting to loading bars and delete files
    await commonUtils.sleep(2500);

    const startConnectorsSuccess = await startConnectors(connectors.current);

    handleError({ name: 'startConnectors', success: startConnectorsSuccess });

    if (deleteType.current) {
      return;
    }

    //Remove files that users want to discard
    removeWorkerPlugin.forEach(jar =>
      deleteApi(`${jar.name}?group=${jar.group}`),
    );

    setActiveStep(4);

    //Waiting for remove files
    await commonUtils.sleep(1000);

    resetModal();
  };

  const steps = [
    'Stop Pipeline',
    'Stop Workspace',
    'Update Workspace',
    'Start Pipeline',
  ];

  return (
    <>
      <StyledInputFile
        id="fileInput"
        accept=".jar"
        type="file"
        onChange={handleFileSelect}
        onClick={event => {
          /* Allow file to be added multiple times */
          event.target.value = null;
        }}
      />
      <StyledLabel htmlFor="fileInput">
        <Button component="span" text="ADD PLUGIN" />
      </StyledLabel>
      <Main>
        <SortTable
          isLoading={jarIsLoading}
          headRows={headRows}
          rows={workerRows.concat(jarRows)}
          tableName="plugins"
          handleDiscard={() => handleDiscard()}
          handleRestart={() => setIsResetModalOpen(true)}
        />
      </Main>

      <DeleteDialog
        title="Delete File?"
        content={`Are you sure you want to delete the file: ${jarNameToBeDeleted} ? This action cannot be undone!`}
        open={isModalOpen}
        handleClose={() => setIsModalOpen(false)}
        handleConfirm={handleDelete}
        working={deleting}
      />
      <DeleteDialog
        title="Restart Workspace?"
        content={`Are you sure you want to restart the workspace, This action cannot be undone!`}
        open={isResetModalOpen}
        handleClose={() => setIsResetModalOpen(false)}
        handleConfirm={() => handleRestart()}
        confirmText="RESTART"
      />
      <Progress
        open={isLoading}
        steps={steps}
        activeStep={activeStep}
        deleteType={deleteType.current}
        maxWidth="md"
        createTitle="Update Workspace..."
        deleteTitle="Failed to update workspace and revert to previous version..."
      />
    </>
  );
};

Plugins.propTypes = {
  worker: PropTypes.shape({
    settings: PropTypes.shape({
      name: PropTypes.string.isRequired,
      jarInfos: PropTypes.array.isRequired,
    }),
  }).isRequired,
  workerRefetch: PropTypes.func.isRequired,
};

export default Plugins;
