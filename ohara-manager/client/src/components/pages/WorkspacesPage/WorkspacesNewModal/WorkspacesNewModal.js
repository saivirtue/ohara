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
import List from '@material-ui/core/List';
import ListItem from '@material-ui/core/ListItem';
import Checkbox from '@material-ui/core/Checkbox';
import ListItemIcon from '@material-ui/core/ListItemIcon';
import ListItemText from '@material-ui/core/ListItemText';
import DialogContent from '@material-ui/core/DialogContent';
import { Form, Field } from 'react-final-form';
import { Link } from 'react-router-dom';
import { isEmpty, get, split, isUndefined, uniq } from 'lodash';
import { ERROR } from 'jest-validate/build/utils';

import * as s from './styles';
import * as generate from 'utils/generate';
import * as MESSAGES from 'constants/messages';
import * as URLS from 'constants/urls';
import * as useApi from 'components/controller';
import * as URL from 'components/controller/url';
import useSnackbar from 'components/context/Snackbar/useSnackbar';
import useCreateServices from './useCreateServices';
import validate from './validate';
import { Label } from 'components/common/Form';
import { Progress } from 'components/common/Mui/Feedback';
import { Button } from 'components/common/Mui/Form';
import { Dialog } from 'components/common/Mui/Dialog';
import { InputField } from 'components/common/Mui/Form';

const WorkerNewModal = props => {
  const [checkedNodes, setCheckedNodes] = useState([]);
  const [checkedFiles, setCheckedFiles] = useState([]);
  const [jars, setJars] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [activeStep, setActiveStep] = useState(0);
  const [deleteType, setDeleteType] = useState(false);
  const { showMessage } = useSnackbar();
  const { getData: jarRes, uploadApi } = useApi.useUploadApi(URL.FILE_URL);
  const { deleteApi: deleteJar } = useApi.useDeleteApi(URL.FILE_URL);
  const { deleteApi: deleteWorker } = useApi.useDeleteApi(URL.WORKER_URL);
  const { deleteApi: deleteBroker } = useApi.useDeleteApi(URL.BROKER_URL);
  const { deleteApi: deleteZookeeper } = useApi.useDeleteApi(URL.ZOOKEEPER_URL);
  const { data: nodes } = useApi.useFetchApi(URL.NODE_URL);
  const { putApi: putZookeeper } = useApi.usePutApi(URL.ZOOKEEPER_URL);
  const { putApi: putBroker } = useApi.usePutApi(URL.BROKER_URL);
  const { putApi: putWorker } = useApi.usePutApi(URL.WORKER_URL);
  const { waitApi, getFinish } = useApi.useWaitApi();
  const {
    create: createZookeeper,
    handleFail: handleZookeeper,
    getResponse: getZookeeperRes,
  } = useCreateServices(URL.ZOOKEEPER_URL);
  const {
    create: createBroker,
    handleFail: handleBroker,
    getResponse: getBrokerRes,
  } = useCreateServices(URL.BROKER_URL);
  const { create: createWorker, handleFail: handleWorker } = useCreateServices(
    URL.WORKER_URL,
  );

  let workingServices = [];
  let plugins = [];

  const uploadJar = async params => {
    const { file, workerName } = params;
    const formData = new FormData();
    formData.append('file', file);
    formData.append('group', workerName);
    formData.append('tags', '{"type":"plugin"}');
    await uploadApi(formData);

    const isSuccess = get(jarRes(), 'data.isSuccess', false);
    if (isSuccess) {
      const jar = jarRes().data.result;
      const newPlugins = [...plugins, jar];
      plugins = newPlugins;
    }
  };

  const handleNodeToggle = value => () => {
    const currentIndex = checkedNodes.indexOf(value);
    const newChecked = [...checkedNodes];

    if (currentIndex === -1) {
      newChecked.push(value);
    } else {
      newChecked.splice(currentIndex, 1);
    }

    setCheckedNodes(newChecked);
  };

  const handleFileToggle = value => () => {
    const currentIndex = checkedFiles.indexOf(value);
    const newChecked = [...checkedFiles];

    if (currentIndex === -1) {
      newChecked.push(value);
    } else {
      newChecked.splice(currentIndex, 1);
    }

    setCheckedFiles(newChecked);
  };

  const handleModalClose = () => {
    props.onClose();
  };

  const resetModal = form => {
    setTimeout(form.reset);
    setJars([]);
    setCheckedFiles([]);
    setCheckedNodes([]);
    handleModalClose();
    setIsLoading(false);
    plugins = [];
    setActiveStep(0);
    setDeleteType(false);
  };

  const waitService = async params => {
    const { service, name, type } = params;
    const deleteCheckFn = res => {
      return !res.data.result.some(e => e.name === name);
    };
    const stopCheckFn = res => {
      return isUndefined(get(res, 'data.result.state', undefined));
    };
    let checkFn;
    if (type === 'delete') {
      checkFn = deleteCheckFn;
    } else if (type === 'stop') {
      checkFn = stopCheckFn;
    }
    switch (service) {
      case 'zookeeper':
        const zkUrl =
          type === 'stop' ? `${URL.ZOOKEEPER_URL}/${name}` : URL.ZOOKEEPER_URL;
        const zkParams = {
          url: zkUrl,
          checkFn,
          sleep: 3000,
        };
        await waitApi(zkParams);
        if (!getFinish()) showMessage(`Failed to delete ${service}: ${name}`);
        break;

      case 'broker':
        const bkUrl =
          type === 'stop' ? `${URL.BROKER_URL}/${name}` : URL.BROKER_URL;
        const bkParams = {
          url: bkUrl,
          checkFn,
          sleep: 3000,
        };
        await waitApi(bkParams);
        if (!getFinish()) showMessage(`Failed to delete ${service}: ${name}`);
        break;

      case 'worker':
        const wkUrl =
          type === 'stop' ? `${URL.WORKER_URL}/${name}` : URL.WORKER_URL;
        const wkParams = {
          url: wkUrl,
          checkFn,
          sleep: 3000,
        };
        await waitApi(wkParams);
        if (!getFinish()) showMessage(`Failed to delete ${service}: ${name}`);
        break;

      default:
        break;
    }
  };

  const saveService = params => {
    const newService = [...workingServices, params];
    workingServices = newService;
  };

  const deleteAllServices = async () => {
    setDeleteType(true);
    plugins.forEach(plugin => {
      const { name, group } = plugin;
      deleteJar(`${name}?group=${group}`);
    });

    const wks = workingServices.filter(working => working.service === 'worker');
    const bks = workingServices.filter(working => working.service === 'broker');
    const zks = workingServices.filter(
      working => working.service === 'zookeeper',
    );
    setActiveStep(3);
    await Promise.all(
      wks.map(async wk => {
        await putWorker(`/${wk.name}/stop`);
        await waitService({
          service: 'worker',
          name: wk.name,
          type: 'stop',
        });
        await deleteWorker(`${wk.name}`);
        await waitService({
          service: 'worker',
          name: wk.name,
          type: 'delete',
        });
      }),
    );
    setActiveStep(2);
    await Promise.all(
      bks.map(async bk => {
        await putBroker(`/${bk.name}/stop`);
        await waitService({
          service: 'broker',
          name: bk.name,
          type: 'stop',
        });
        await deleteBroker(`${bk.name}`);
        await waitService({
          service: 'broker',
          name: bk.name,
          type: 'delete',
        });
      }),
    );

    setActiveStep(1);
    await Promise.all(
      zks.map(async zk => {
        await putZookeeper(`/${zk.name}/stop`);
        await waitService({
          service: 'zookeeper',
          name: zk.name,
          type: 'stop',
        });
        await deleteZookeeper(`${zk.name}`);
        await waitService({
          service: 'zookeeper',
          name: zk.name,
          type: 'delete',
        });
      }),
    );
    setActiveStep(0);
  };

  const createServices = async values => {
    const nodeNames = checkedNodes;

    const checkResult = res => {
      return 'RUNNING' === get(res, 'data.result.state', null);
    };
    const prefix = window.servicePrefix ? window.servicePrefix : '';

    const zookeeperName = generate.serviceName({
      prefix: `${prefix}zk`,
      length: 5,
    });

    const zookeeperPostParams = {
      name: zookeeperName,
      clientPort: generate.port(),
      peerPort: generate.port(),
      electionPort: generate.port(),
      // TODO: #1632 there's a couple of rules when creating services
      // - Rule one: if the nodes supplied by users are less than 3. Creates a zookeeper and
      // the equal amount of brokers and workers of nodes
      // - Rule two: on the other hand, if users provide more than 3 nodes. We create 3 zookeepers
      // and equal amount of brokers and workers of nodes
      // due to a bug in #696. ohara is currently unable to create more than three zookeepers. So we're
      // now specifying only one zookeeper when creating services. `nodeNames[0]`👇
      // and once the bug is fixed we will then apply the rule two in frontend :)
      nodeNames: [nodeNames[0]],
    };
    await createZookeeper({
      postParams: zookeeperPostParams,
      checkResult,
      sleep: 3000,
    });
    if (handleZookeeper()) throw new ERROR();
    setActiveStep(1);
    saveService({ service: 'zookeeper', name: zookeeperName });

    const brokerClusterName = generate.serviceName({
      prefix: `${prefix}bk`,
      length: 5,
    });

    const brokerPostParams = {
      name: brokerClusterName,
      zookeeperClusterName: zookeeperName,
      clientPort: generate.port(),
      exporterPort: generate.port(),
      jmxPort: generate.port(),
      nodeNames,
    };
    await createBroker({
      postParams: brokerPostParams,
      checkResult,
      sleep: 3000,
    });
    if (handleBroker()) throw new ERROR();
    setActiveStep(2);
    saveService({ service: 'broker', name: brokerClusterName });

    const jarKeys = plugins.map(jar => {
      return {
        name: jar.name,
        group: jar.group,
      };
    });

    const workerPostParams = {
      name: values.name,
      nodeNames,
      jarKeys,
      jmxPort: generate.port(),
      clientPort: generate.port(),
      brokerClusterName,
      groupId: generate.serviceName(),
      configTopicName: generate.serviceName(),
      offsetTopicName: generate.serviceName(),
      statusTopicName: generate.serviceName(),
      freePorts: [
        generate.port(),
        generate.port(),
        generate.port(),
        generate.port(),
        generate.port(),
      ],
      tags: {
        broker: {
          name: getBrokerRes().data.result.settings.name,
          imageName: getBrokerRes().data.result.settings.imageName,
        },
        zookeeper: {
          name: getZookeeperRes().data.result.settings.name,
          imageName: getZookeeperRes().data.result.settings.imageName,
        },
      },
    };

    await createWorker({
      postParams: workerPostParams,
      sleep: 3000,
      checkResult: response => {
        const { connectors } = response.data.result;
        return !isEmpty(connectors);
      },
    });

    if (handleWorker()) throw new ERROR();
    setActiveStep(3);
    saveService({ service: 'worker', name: values.name });
  };

  const onSubmit = async (values, form) => {
    setIsLoading(true);
    await Promise.all(
      checkedFiles.map(async file => {
        const params = {
          file,
          workerName: values.name,
        };
        await uploadJar(params);
      }),
    );

    try {
      await createServices(values);
    } catch (error) {
      // Ignore the error, that's handled in the API request handler
      await deleteAllServices();
      resetModal(form);
      return;
    }
    showMessage(MESSAGES.SERVICE_CREATION_SUCCESS);
    props.onConfirm();
    resetModal(form);
  };

  const handleFileSelect = e => {
    const file = e.target.files[0];
    const newJars = [...jars];
    if (file) {
      newJars.push(file);
    }
    setJars(uniq(newJars));
  };

  const steps = ['Zookeeper', 'Broker', 'Worker'];

  return (
    <Form
      onSubmit={onSubmit}
      initialValues={{}}
      validate={validate}
      render={({
        handleSubmit,
        form,
        submitting,
        pristine,
        invalid,
        values,
      }) => {
        return (
          <>
            <Dialog
              testId="new-workspace-modal"
              scroll="paper"
              title="New workspace"
              open={props.isActive}
              handleClose={() => {
                if (!submitting) resetModal(form);
              }}
              handleConfirm={handleSubmit}
              confirmDisabled={
                submitting || invalid || pristine || checkedNodes.length === 0
              }
            >
              {isEmpty(nodes) ? (
                <s.StyledWarning
                  data-testid="redirect-warning"
                  text={
                    <>
                      {`You don't have any nodes available yet. But you can create one in `}
                      <Link to={URLS.NODES}>here</Link>
                    </>
                  }
                />
              ) : (
                <s.StyledDialogDividers dividers>
                  <s.StyledInputFile
                    id="fileInput"
                    accept=".jar"
                    type="file"
                    onChange={handleFileSelect}
                  />
                  <form onSubmit={handleSubmit}>
                    <DialogContent>
                      <Field
                        label="Name"
                        name="name"
                        component={InputField}
                        placeholder="cluster00"
                        data-testid="name-input"
                        disabled={submitting}
                        autoFocus
                        required
                      />
                    </DialogContent>
                    <s.StyledDialogContent>
                      <Label>Node List</Label>
                      <s.StyledPaper>
                        <List>
                          {get(nodes, 'data.result', []).map(node => {
                            const { name } = node;
                            return (
                              <ListItem
                                key={name}
                                dense
                                button
                                onClick={handleNodeToggle(name)}
                              >
                                <ListItemIcon>
                                  <Checkbox
                                    color="primary"
                                    edge="start"
                                    checked={checkedNodes.indexOf(name) !== -1}
                                    tabIndex={-1}
                                    disableRipple
                                  />
                                </ListItemIcon>
                                <ListItemText
                                  id={name}
                                  primary={name}
                                  data-testid={name}
                                />
                              </ListItem>
                            );
                          })}
                        </List>
                      </s.StyledPaper>
                    </s.StyledDialogContent>
                    <s.StyledDialogContent>
                      <Label>Plugin List</Label>
                      <s.StyledPaper>
                        <List>
                          {jars.map(jar => {
                            const { name } = jar;
                            return (
                              <ListItem
                                key={name}
                                dense
                                button
                                onClick={handleFileToggle(jar)}
                              >
                                <ListItemIcon>
                                  <Checkbox
                                    color="primary"
                                    edge="start"
                                    checked={checkedFiles.indexOf(jar) !== -1}
                                    tabIndex={-1}
                                    disableRipple
                                  />
                                </ListItemIcon>
                                <ListItemText
                                  id={jar}
                                  primary={split(name, '.jar', 1)}
                                />
                              </ListItem>
                            );
                          })}
                          <s.StyledLabel htmlFor="fileInput">
                            <Button component="span" text="NEW PLUGIN" />
                          </s.StyledLabel>
                        </List>
                      </s.StyledPaper>
                    </s.StyledDialogContent>
                  </form>
                </s.StyledDialogDividers>
              )}
            </Dialog>
            <Progress
              open={isLoading}
              steps={steps}
              activeStep={activeStep}
              deleteType={deleteType}
              deleteTitle="Failed to create services, deleting the workspace…"
            />
          </>
        );
      }}
    />
  );
};

WorkerNewModal.propTypes = {
  isActive: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  onConfirm: PropTypes.func.isRequired,
};

export default WorkerNewModal;
