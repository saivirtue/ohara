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

import { createRoutine } from 'redux-routines';

export const initializeRoutine = createRoutine('INITIALIZE');
export const fetchTopicsRoutine = createRoutine('FETCH_TOPICS');
export const createTopicRoutine = createRoutine('CREATE_TOPIC');
export const updateTopicRoutine = createRoutine('UPDATE_TOPIC');
export const deleteTopicRoutine = createRoutine('DELETE_TOPIC');
export const startTopicRoutine = createRoutine('START_TOPIC');
export const stopTopicRoutine = createRoutine('STOP_TOPIC');
export const fetchTopicDataRoutine = createRoutine('FETCH_TOPIC_DATA');
