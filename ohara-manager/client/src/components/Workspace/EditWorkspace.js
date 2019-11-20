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
import { get } from 'lodash';
import IconButton from '@material-ui/core/IconButton';
import Tabs from '@material-ui/core/Tabs';
import Tab from '@material-ui/core/Tab';
import MoreIcon from '@material-ui/icons/MoreVert';
import Divider from '@material-ui/core/Divider';
import Button from '@material-ui/core/Button';
import Typography from '@material-ui/core/Typography';
import Box from '@material-ui/core/Box';

import { FullScreenDialog } from 'components/common/Dialog';
import { useEditWorkspaceDialog, useAddTopicDialog } from 'context';
import { TopicsTab } from 'components/Workspace/TopicsTab';
import { AddTopicDialog } from 'components/Topic';
import { StyledActions } from './Styles';

function TabPanel(props) {
  const { children, value, index, ...other } = props;

  return (
    <Typography
      component="div"
      role="tab-panel"
      hidden={value !== index}
      id={`edit-workspace-tab-panel-${index}`}
      aria-labelledby={`edit-workspace-tab-${index}`}
      {...other}
    >
      <Box p={3}>{children}</Box>
    </Typography>
  );
}

TabPanel.propTypes = {
  children: PropTypes.node.isRequired,
  index: PropTypes.any.isRequired,
  value: PropTypes.any.isRequired,
};

const EditWorkspace = () => {
  const {
    isOpen: isEditWorkspaceDialogOpen,
    close: closeEditWorkspaceDialog,
    data: editWorkspaceDialogData,
    setData: setEditWorkspaceDialogData,
  } = useEditWorkspaceDialog();
  const { open: openAddTopicDialog } = useAddTopicDialog();

  const handleChange = (event, newTab) => {
    setEditWorkspaceDialogData({ tab: newTab });
  };

  const tab = get(editWorkspaceDialogData, 'tab', 'overview');
  return (
    <>
      <FullScreenDialog
        title="Your workspace for oharadevteam"
        open={isEditWorkspaceDialogOpen}
        handleClose={closeEditWorkspaceDialog}
      >
        <StyledActions>
          <Button
            variant="contained"
            color="primary"
            onClick={openAddTopicDialog}
          >
            ADD TOPIC
          </Button>
          <Button variant="outlined" color="primary">
            UPLOAD FILE
          </Button>
          <IconButton
            aria-label="display more actions"
            edge="end"
            color="inherit"
          >
            <MoreIcon />
          </IconButton>
        </StyledActions>
        <Tabs
          value={tab}
          onChange={handleChange}
          indicatorColor="primary"
          textColor="primary"
        >
          <Tab label="Overview" value="overview" />
          <Tab label="Topics" value="topics" />
          <Tab label="Files" value="files" />
          <Tab label="Settings" value="settings" />
        </Tabs>
        <Divider />
        <TabPanel value={tab} index={'overview'}>
          Overview
        </TabPanel>
        <TabPanel value={tab} index={'topics'}>
          <TopicsTab />
        </TabPanel>
        <TabPanel value={tab} index={'files'}>
          Files
        </TabPanel>
        <TabPanel value={tab} index={'settings'}>
          Settings
        </TabPanel>
      </FullScreenDialog>
      <AddTopicDialog />
    </>
  );
};

export default EditWorkspace;