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
import PropTypes from 'prop-types';
import { useApi } from 'context';
import * as hooks from 'hooks';
import { useEventLog } from 'context/eventLog/eventLogHooks';
import { initializeRoutine } from './fileRoutines';
import { createActions } from './fileActions';
import { reducer, initialState } from './fileReducer';

const FileStateContext = React.createContext();
const FileDispatchContext = React.createContext();

const FileProvider = ({ children }) => {
  const [state, dispatch] = React.useReducer(reducer, initialState);
  const workspaceName = hooks.useWorkspaceName();

  React.useEffect(() => {
    dispatch(initializeRoutine.trigger());
  }, [workspaceName]);

  return (
    <FileStateContext.Provider value={state}>
      <FileDispatchContext.Provider value={dispatch}>
        {children}
      </FileDispatchContext.Provider>
    </FileStateContext.Provider>
  );
};

const useFileState = () => {
  const context = React.useContext(FileStateContext);
  if (context === undefined) {
    throw new Error('useFileState must be used within a FileProvider');
  }
  return context;
};

const useFileDispatch = () => {
  const context = React.useContext(FileDispatchContext);
  if (context === undefined) {
    throw new Error('useFileDispatch must be used within a FileProvider');
  }
  return context;
};

FileProvider.propTypes = {
  children: PropTypes.node.isRequired,
};

const useFileActions = () => {
  const state = useFileState();
  const dispatch = useFileDispatch();
  const eventLog = useEventLog();
  const { fileApi } = useApi();
  return React.useMemo(
    () => createActions({ state, dispatch, eventLog, fileApi }),
    [state, dispatch, eventLog, fileApi],
  );
};

export { FileProvider, useFileState, useFileActions };
