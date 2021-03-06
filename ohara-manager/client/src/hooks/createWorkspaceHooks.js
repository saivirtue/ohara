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

import { useCallback } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { isEqual as isDeepEqual } from 'lodash';

import * as actions from 'store/actions';

export const useIsCreateWorkspaceOpen = () => {
  const mapState = useCallback(state => !!state.ui.createWorkspace.isOpen, []);
  return useSelector(mapState);
};

export const useCreateWorkspaceProgress = () => {
  const mapState = useCallback(state => state.ui.createWorkspace.progress, []);
  return useSelector(mapState, isDeepEqual);
};

export const useCreateWorkspaceState = () => {
  const mapState = useCallback(state => state.ui.createWorkspace, []);
  return useSelector(mapState, isDeepEqual);
};

export const useOpenCreateWorkspaceAction = () => {
  const dispatch = useDispatch();
  return useCallback(() => dispatch(actions.openCreateWorkspace.trigger()), [
    dispatch,
  ]);
};

export const useCloseCreateWorkspaceAction = () => {
  const dispatch = useDispatch();
  return useCallback(() => dispatch(actions.closeCreateWorkspace.trigger()), [
    dispatch,
  ]);
};
