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

import React, { useState } from 'react';
import PropTypes from 'prop-types';
import moment from 'moment';
import { isEmpty } from 'lodash';

import RefreshIcon from '@material-ui/icons/Refresh';
import SearchIcon from '@material-ui/icons/Search';
import OpenInNewIcon from '@material-ui/icons/OpenInNew';
import CloseIcon from '@material-ui/icons/Close';
import IconButton from '@material-ui/core/IconButton';
import Tab from '@material-ui/core/Tab';
import Tabs from '@material-ui/core/Tabs';
import Grid from '@material-ui/core/Grid';
import Popover from '@material-ui/core/Popover';
import TextField from '@material-ui/core/TextField';
import Radio from '@material-ui/core/Radio';
import RadioGroup from '@material-ui/core/RadioGroup';
import FormControlLabel from '@material-ui/core/FormControlLabel';

import * as logApi from 'api/logApi';
import DevToolSelect from './DevToolSelect';
import { tabName } from '../DevToolDialog';
import { Button } from 'components/common/Form';
import { Tooltip } from 'components/common/Tooltip';
import {
  StyledHeader,
  StyledSearchBody,
  StyledTextField,
} from './HeaderStyles';

const Header = props => {
  const { tabIndex, data, topics, ...others } = props;
  const {
    handleTabChange,
    closeDialog,
    setDataDispatch,
    fetchTopicData,
    fetchLogs,
    pipelineName,
  } = others;

  const [timeSeconds, setTimeSeconds] = useState(600);
  const [selectAnchor, setSelectAnchor] = useState(null);
  const [searchAnchor, setSearchAnchor] = useState(null);
  const [searchTimeGroup, setSearchTimeGroup] = useState('latest');

  const handleSelectAnchor = event => {
    setSelectAnchor(event.currentTarget);
  };

  const handleSelect = (event, selectName) => {
    setDataDispatch({ [selectName]: event.target.value });
  };

  const handleRefresh = () => {
    if (tabIndex === tabName.topic) {
      fetchTopicData(data.topicLimit);
    } else {
      fetchLogs(timeSeconds, data.hostname);
    }
  };

  const handleSearchClick = event => {
    setSearchAnchor(event.currentTarget);
  };

  const handleRadioChange = event => {
    setSearchTimeGroup(event.target.value);
    if (
      event.target.value === 'customize' &&
      !data.startTime &&
      !data.endTime
    ) {
      // for initial the form, we need to set value explicitly
      setDataDispatch({
        startTime: moment()
          .subtract(10, 'minutes')
          .format('YYYY-MM-DD[T]hh:mm'),
      });
      setDataDispatch({
        endTime: moment().format('YYYY-MM-DD[T]hh:mm'),
      });
    }
  };

  const handleQueryButtonClick = () => {
    if (tabIndex === tabName.topic) {
      fetchTopicData(data.topicLimit);
    } else {
      let time = 0;
      if (searchTimeGroup === 'latest') {
        // timeRange uses minute units
        time = data.timeRange * 60;
      } else {
        time = Math.ceil(
          moment
            .duration(moment(data.endTime).diff(moment(data.startTime)))
            .asSeconds(),
        );
      }

      fetchLogs(time, data.hostname);
      setTimeSeconds(time);
      setDataDispatch({ timeGroup: searchTimeGroup });
    }
  };

  const handleOpenNewWindow = () => {
    if (tabIndex === tabName.topic) {
      const privateTopic =
        // Only private topics are able to use capital letters as name
        data.topicName.startsWith('T') &&
        topics.find(topic => topic.tags.displayName === data.topicName);

      if (data.topicName)
        window.open(
          `${window.location}/view?type=${tabName.topic}&topic=${
            privateTopic ? privateTopic.name : data.topicName
          }&limit=${data.topicLimit}`,
        );
    } else {
      if (data.hostLog) {
        window.open(
          `${window.location}/view?type=${tabName.log}&service=${data.service}`
            .concat(`&hostname=${data.hostname}&timeSeconds=${timeSeconds}`)
            .concat(`&stream=${data.stream}&pipelineName=${pipelineName}`),
        );
      }
    }
  };

  const getDisableState = data => {
    const { type, topicName, service, isLoading, stream } = data;

    // true -> disabled
    if (isLoading) return true;
    if (type === 'topics' && !topicName) return true;
    if (type === 'logs' && !service) return true;
    if (type === 'logs' && (service === 'stream' && !stream)) return true;
  };

  return (
    <StyledHeader
      container
      direction="row"
      justify="space-between"
      alignItems="center"
    >
      <Grid item xs={4} lg={4}>
        <Tabs
          value={tabIndex}
          indicatorColor="primary"
          textColor="primary"
          onChange={handleTabChange}
        >
          <Tab
            value={tabName.topic}
            label={tabName.topic}
            disabled={data.isLoading}
          />
          <Tab
            value={tabName.log}
            label={tabName.log}
            disabled={data.isLoading}
          />
        </Tabs>
      </Grid>

      <Grid item xs={8} lg={7}>
        <div className="items">
          <DevToolSelect
            index={tabName.topic}
            currentTab={tabIndex}
            value={data.topicName}
            onChange={event => handleSelect(event, 'topicName')}
            list={topics.map(topic => {
              // Private topic names are stored in tags, the name field is randomly generated.
              return topic.tags.type === 'shared'
                ? topic.name
                : topic.tags.displayName;
            })}
            setAnchor={handleSelectAnchor}
            anchor={selectAnchor}
            disabled={data.isLoading}
          />
          <DevToolSelect
            index={tabName.log}
            currentTab={tabIndex}
            value={data.service}
            onChange={event => handleSelect(event, 'service')}
            list={Object.keys(logApi.logServices)}
            setAnchor={handleSelectAnchor}
            anchor={selectAnchor}
            disabled={data.isLoading}
          />
          {data.service === 'stream' && (
            <DevToolSelect
              index={tabName.log}
              currentTab={tabIndex}
              value={data.stream}
              onChange={event => handleSelect(event, 'stream')}
              list={data.streams}
              setAnchor={handleSelectAnchor}
              anchor={selectAnchor}
              disabled={data.isLoading}
            />
          )}

          <DevToolSelect
            disabled={
              !data.service ||
              (data.service === 'stream' && !data.stream) ||
              data.isLoading
            }
            index={tabName.log}
            currentTab={tabIndex}
            value={data.hostname}
            onChange={event => handleSelect(event, 'hostname')}
            list={data.hosts}
            setAnchor={handleSelectAnchor}
            anchor={selectAnchor}
          />

          <Tooltip title="Fetch the data again">
            <IconButton
              className="item"
              disabled={getDisableState(data)}
              onClick={handleRefresh}
              size="small"
            >
              <RefreshIcon />
            </IconButton>
          </Tooltip>

          <Tooltip title="Query with different parameters">
            <IconButton
              disabled={getDisableState(data)}
              className="item"
              onClick={handleSearchClick}
              size="small"
            >
              <SearchIcon />
            </IconButton>
          </Tooltip>

          <Tooltip title="Open in a new window">
            <IconButton
              className="item"
              onClick={handleOpenNewWindow}
              size="small"
              disabled={data.type === 'topics' && !data.topicName}
            >
              <OpenInNewIcon />
            </IconButton>
          </Tooltip>

          <Tooltip title="Close this panel">
            <IconButton className="item" onClick={closeDialog} size="small">
              <CloseIcon />
            </IconButton>
          </Tooltip>

          <Popover
            open={Boolean(searchAnchor)}
            anchorEl={searchAnchor}
            onClose={() => setSearchAnchor(null)}
            anchorOrigin={{
              vertical: 'bottom',
              horizontal: 'center',
            }}
            transformOrigin={{
              vertical: 'top',
              horizontal: 'right',
            }}
          >
            {tabIndex === tabName.topic ? (
              <StyledSearchBody tab={tabIndex}>
                <label>Rows per query</label>
                <TextField
                  type="number"
                  value={data.topicLimit}
                  onChange={event =>
                    setDataDispatch({ topicLimit: Number(event.target.value) })
                  }
                  disabled={isEmpty(data.topicName)}
                />
                <Button
                  variant="contained"
                  onClick={handleQueryButtonClick}
                  disabled={isEmpty(data.topicName)}
                >
                  QUERY
                </Button>
              </StyledSearchBody>
            ) : (
              <StyledSearchBody tab={tabIndex}>
                <RadioGroup
                  value={searchTimeGroup}
                  onChange={handleRadioChange}
                >
                  <FormControlLabel
                    value="latest"
                    control={<Radio color="primary" />}
                    label="Latest"
                    disabled={isEmpty(data.service)}
                  />
                  <label>Minutes per query</label>
                  <TextField
                    disabled={
                      isEmpty(data.service) || searchTimeGroup !== 'latest'
                    }
                    type="number"
                    value={data.timeRange}
                    onChange={event =>
                      setDataDispatch({ timeRange: Number(event.target.value) })
                    }
                  />
                </RadioGroup>
                <RadioGroup
                  disabled={isEmpty(data.service)}
                  value={searchTimeGroup}
                  onChange={handleRadioChange}
                >
                  <FormControlLabel
                    value="customize"
                    control={<Radio color="primary" />}
                    label="Customize"
                    disabled={isEmpty(data.service)}
                  />
                  <StyledTextField
                    disabled={
                      isEmpty(data.service) || searchTimeGroup !== 'customize'
                    }
                    label="Start date"
                    type="datetime-local"
                    value={
                      data.startTime ||
                      moment()
                        .subtract(10, 'minutes')
                        .format('YYYY-MM-DD[T]hh:mm')
                    }
                    InputLabelProps={{
                      shrink: true,
                    }}
                    onChange={event =>
                      setDataDispatch({ startTime: event.target.value })
                    }
                  />
                  <StyledTextField
                    disabled
                    label="End date"
                    type="datetime-local"
                    value={
                      data.endTime || moment().format('YYYY-MM-DD[T]hh:mm')
                    }
                    InputLabelProps={{
                      shrink: true,
                    }}
                    onChange={event =>
                      setDataDispatch({ endTime: event.target.value })
                    }
                  />
                </RadioGroup>
                <Button
                  onClick={handleQueryButtonClick}
                  disabled={isEmpty(data.service)}
                >
                  QUERY
                </Button>
              </StyledSearchBody>
            )}
          </Popover>
        </div>
      </Grid>
    </StyledHeader>
  );
};

Header.propTypes = {
  tabIndex: PropTypes.string.isRequired,
  data: PropTypes.shape({
    type: PropTypes.string.isRequired,
    isLoading: PropTypes.bool.isRequired,
    service: PropTypes.string.isRequired,
    topicLimit: PropTypes.number,
    topicName: PropTypes.string,
    hosts: PropTypes.array,
    streams: PropTypes.array,
    stream: PropTypes.string,
    hostname: PropTypes.string,
    timeRange: PropTypes.number,
    startTime: PropTypes.string,
    endTime: PropTypes.string,
    hostLog: PropTypes.array,
  }),
  topics: PropTypes.array.isRequired,
  handleTabChange: PropTypes.func.isRequired,
  closeDialog: PropTypes.func.isRequired,
  setDataDispatch: PropTypes.func.isRequired,
  others: PropTypes.node,
};

export default Header;
