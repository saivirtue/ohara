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

import { merge } from 'lodash';
import { normalize } from 'normalizr';
import { combineEpics, ofType } from 'redux-observable';
import { of, zip } from 'rxjs';
import { catchError, map, mergeMap, switchMap } from 'rxjs/operators';

import * as pipelineApi from 'api/pipelineApi';
import * as workspaceApi from 'api/workspaceApi';
import * as actions from 'store/actions';
import * as schema from 'store/schema';

const initializeAppEpic = action$ =>
  action$.pipe(
    ofType(actions.initializeApp.TRIGGER),
    switchMap(() =>
      zip(pipelineApi.getAll(), workspaceApi.getAll()).pipe(
        map(([plRes, wsRes]) => [
          normalize(plRes.data, [schema.pipeline]),
          normalize(wsRes.data, [schema.workspace]),
        ]),
        mergeMap(([plEntities, wsEntities]) =>
          of(
            actions.initializeApp.success(merge(plEntities, wsEntities)),
            actions.initializeApp.fulfill(),
          ),
        ),
        catchError(res => of(actions.initializeApp.failure(res))),
      ),
    ),
  );

export default combineEpics(initializeAppEpic);
