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
import { isEmpty } from 'lodash';
import IconButton from '@material-ui/core/IconButton';
import RefreshIcon from '@material-ui/icons/Refresh';
import SearchIcon from '@material-ui/icons/Search';
import OpenInNewIcon from '@material-ui/icons/OpenInNew';
import TextField from '@material-ui/core/TextField';

import * as hooks from 'hooks';
import { Tooltip } from 'components/common/Tooltip';
import { Button } from 'components/common/Form';
import Popover from 'components/common/Popover';
import Select from 'components/common/Select';
import { TAB } from 'context/devTool/const';
import { usePrevious } from 'utils/hooks';
import { StyledSearchBody } from './ControllerStyles';

const ControllerTopic = () => {
  const topics = hooks.useTopicsInPipeline();

  const setTopicQueryParams = hooks.useSetDevToolTopicQueryParams();
  const refetchTopic = hooks.useRefetchDevToolTopicDataAction();
  const { query, isFetching } = hooks.useDevToolTopicData();
  const { name, limit } = query;

  const prevName = usePrevious(name);

  const handleOpenNewWindow = () => {
    if (name)
      window.open(
        `${window.location}/view?type=${TAB.topic}&topicName=${name}&topicLimit=${limit}`,
      );
  };

  const getDisableState = () => {
    // true -> disabled
    if (isFetching) return true;
    if (isEmpty(name)) return true;
    if (isEmpty(topics)) return true;
  };

  return (
    <>
      <Select
        disabled={isFetching || isEmpty(topics)}
        list={topics.map((topic) => ({
          displayName: topic.displayName,
          value: topic.name,
        }))}
        onChange={(event) =>
          prevName !== event.target.value &&
          setTopicQueryParams({ name: event.target.value })
        }
        testId="devtool-topic-list"
        value={name || ''}
      />
      <Tooltip title="Fetch the data again">
        <IconButton
          className="item"
          disabled={getDisableState()}
          onClick={refetchTopic}
          size="small"
        >
          <RefreshIcon />
        </IconButton>
      </Tooltip>
      <Popover
        showTooltip
        tooltipTitle="Query with different parameters"
        trigger={
          <IconButton
            className="item"
            disabled={getDisableState()}
            size="small"
          >
            <SearchIcon />
          </IconButton>
        }
      >
        <StyledSearchBody tab={TAB.topic}>
          <label>Rows per query</label>
          <TextField
            defaultValue={limit}
            disabled={isEmpty(name)}
            onChange={(event) =>
              setTopicQueryParams({ limit: Number(event.target.value) })
            }
            type="number"
          />
          <Button
            disabled={isEmpty(name)}
            onClick={refetchTopic}
            variant="contained"
          >
            QUERY
          </Button>
        </StyledSearchBody>
      </Popover>
      <Tooltip title="Open in a new window">
        <IconButton
          className="item"
          disabled={getDisableState()}
          onClick={handleOpenNewWindow}
          size="small"
        >
          <OpenInNewIcon />
        </IconButton>
      </Tooltip>
    </>
  );
};

export { ControllerTopic };
