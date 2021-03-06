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

export const fetchEventLogsRoutine = createRoutine('FETCH_EVENT_LOGS');
export const createEventLogRoutine = createRoutine('CREATE_EVENT_LOG');
export const deleteEventLogsRoutine = createRoutine('DELETE_EVENT_LOGS');
export const clearEventLogsRoutine = createRoutine('CLEAR_EVENT_LOGS');

export const fetchSettingsRoutine = createRoutine('FETCH_SETTINGS');
export const updateSettingsRoutine = createRoutine('UPDATE_SETTINGS');

export const fetchNotificationsRoutine = createRoutine('FETCH_NOTIFICATIONS');
export const updateNotificationsRoutine = createRoutine('UPDATE_NOTIFICATIONS');
export const clearNotificationsRoutine = createRoutine('CLEAR_NOTIFICATIONS');
