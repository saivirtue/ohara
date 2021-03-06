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

import { isEmpty } from 'lodash';
import { ofType } from 'redux-observable';
import { of, interval } from 'rxjs';
import { debounce, filter, switchMap } from 'rxjs/operators';

import * as actions from 'store/actions';

export default action$ =>
  action$.pipe(
    ofType(actions.switchPipeline.TRIGGER),
    filter(action => !isEmpty(action.payload)),
    debounce(() => interval(1000)),
    switchMap(action => {
      const { name } = action.payload;
      return of(actions.switchPipeline.success(name));
    }),
  );
