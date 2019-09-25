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

import React, {
  useState,
  useEffect,
  useImperativeHandle,
  forwardRef,
} from 'react';
import PropTypes from 'prop-types';
import { Link } from 'react-router-dom';
import { get, truncate, isEmpty } from 'lodash';

import * as jarApi from 'api/jarApi';
import * as URLS from 'constants/urls';
import { ListLoader } from 'components/common/Loader';
import { Modal } from 'components/common/Modal';
import { Select } from 'components/common/Form';
import { createConnector } from './pipelineToolbarUtils';
import { Wrapper, Inner, LoaderWrapper } from './styles';
import { Input, FormGroup } from 'components/common/Form';
import { Warning } from 'components/common/Messages';

const PipelineNewStream = forwardRef((props, ref) => {
  const [newStreamAppName, setNewStreamAppName] = useState('');
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [jars, setJars] = useState([]);
  const [activeJar, setActiveJar] = useState({});

  const {
    workerClusterName,
    enableAddButton,
    updateGraph,
    pipelineGroup,
    brokerClusterName,
  } = props;
  const jarGroup = workerClusterName;

  useEffect(() => {
    const fetchJars = async () => {
      const response = await jarApi.fetchJars(jarGroup);
      setIsLoading(false);

      const jars = get(response, 'data.result', null);
      const activeJar = {
        group: get(jars, '[0].group', null),
        name: get(jars, '[0].name', null),
      };

      if (!isEmpty(jars)) {
        setJars(jars);
        setActiveJar(activeJar);
        enableAddButton(false);
      }
    };

    fetchJars();
  }, [jarGroup, enableAddButton]);

  useImperativeHandle(ref, () => ({
    update() {
      setIsModalOpen(true);
    },
  }));

  const handleChange = ({ target: { value } }) => {
    const newStreamAppName = truncate(value.replace(/[^0-9a-z]/g, ''), {
      length: 20,
      omission: '',
    });
    setNewStreamAppName(newStreamAppName);
  };

  const handleConfirm = () => {
    const { group, name } = jars.filter(jar => jar.name === activeJar.name)[0];

    const connector = {
      jarKey: { group, name },
      className: 'stream',
      typeName: 'stream',
    };

    createConnector({
      updateGraph,
      connector,
      newStreamAppName,
      brokerClusterName,
      group: pipelineGroup,
    });

    props.handleClose();
  };

  return (
    <div>
      {isLoading ? (
        <LoaderWrapper>
          <ListLoader />
        </LoaderWrapper>
      ) : (
        <Wrapper>
          {isEmpty(jars) ? (
            <Warning
              text={
                <>
                  {`You don't have any stream jars available in this workspace yet. But you can create one in `}
                  <Link
                    to={`${URLS.WORKSPACES}/${workerClusterName}/streamapps`}
                  >
                    here
                  </Link>
                </>
              }
            />
          ) : (
            <Select
              isObject
              list={jars}
              selected={activeJar}
              handleChange={event => setActiveJar(event.target.value)}
              data-testid="streamapp-select"
            />
          )}
        </Wrapper>
      )}

      <Modal
        isActive={isModalOpen}
        title="New StreamApp Name"
        width="370px"
        data-testid="addStreamApp"
        confirmBtnText="Add"
        handleConfirm={handleConfirm}
        handleCancel={() => setIsModalOpen(false)}
      >
        <Inner>
          <FormGroup data-testid="name">
            <Input
              name="name"
              width="100%"
              placeholder="StreamApp name"
              data-testid="name-input"
              value={newStreamAppName}
              handleChange={handleChange}
            />
          </FormGroup>
        </Inner>
      </Modal>
    </div>
  );
});

PipelineNewStream.propTypes = {
  match: PropTypes.shape({
    params: PropTypes.object.isRequired,
  }).isRequired,
  activeConnector: PropTypes.oneOfType([PropTypes.object, PropTypes.string]),
  updateGraph: PropTypes.func.isRequired,
  enableAddButton: PropTypes.func.isRequired,
  workerClusterName: PropTypes.string.isRequired,
  handleClose: PropTypes.func.isRequired,
  pipelineGroup: PropTypes.string.isRequired,
  brokerClusterName: PropTypes.string.isRequired,
};

export default PipelineNewStream;
