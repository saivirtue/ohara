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

import { isNull, isEmpty, isString } from 'lodash';

export const removePrevConnector = (flows, connectorName) => {
  const updateFlows = flows.map(flow => {
    const hasConnection = flow.to.find(t => t.name === connectorName);

    if (hasConnection) {
      const updatedTo = flow.to.filter(t => t.name !== connectorName);
      return { ...flow, to: updatedTo };
    }
    return flow;
  });

  return updateFlows;
};

export const updateFlows = ({
  pipeline,
  update = null,
  sinkName = null,
  streamAppName = null,
  dispatcher,
}) => {
  let {
    flows,
    name: pipelineName,
    group: pipelineGroup,
    tags: { workerClusterName },
  } = pipeline;

  // Remove previous connector from the graph as we're only allowing single
  // connection between a connector to a topic for now
  if (!isNull(sinkName) || !isNull(streamAppName)) {
    const connectorName = sinkName || streamAppName;
    flows = removePrevConnector(flows, connectorName);
  }

  const { name: connectorName, kind, to } = update;
  let updatedFlows = null;

  let group;

  if (kind === 'topic') {
    group = workerClusterName;
  } else if (kind === 'stream') {
    group = `${workerClusterName}${pipelineName}`;
  } else {
    group = pipelineGroup;
  }

  if (dispatcher.name === 'TOOLBAR') {
    // Toolbar update only needs to `add` new connector to the graph
    updatedFlows = [...flows, { from: { group, name: connectorName }, to: [] }];
  } else if (
    dispatcher.name === 'CONNECTOR' ||
    dispatcher.name === 'STREAM_APP'
  ) {
    updatedFlows = flows.map(flow => {
      if (flow.from.name === connectorName) {
        const newTo = to.map(t => {
          const updateGroup =
            dispatcher.name === 'STREAM_APP'
              ? `${workerClusterName}${pipelineName}`
              : group;

          // If the to is a string, let's wrap it with an object
          if (isString(t)) return { group: updateGroup, name: t };
          return t;
        });

        // 1. reset the `to` to an empty array which means there's no
        // topic connected to this connector
        // 2. use the new to
        const updatedTo = isEmpty(to) ? [] : [...newTo];

        const update = {
          ...flow,
          to: updatedTo,
        };

        return update;
      }
      return flow;
    });
  }
  // If there's no update in flows, return the original flows
  return isNull(updatedFlows) ? flows : updatedFlows;
};

export const updateSingleGraph = (graph, name, transformer) => {
  return graph.map(g => {
    if (g.name === name) {
      return { ...transformer(g) };
    }

    return g;
  });
};

export const cleanPrevFromTopics = (graph, connectorName) => {
  // See if the connectorName is connected with a topic in the graph
  const prevTopic = graph.find(g => g.to.includes(connectorName));

  if (prevTopic) {
    // Remove previous "form topic"
    const prevTopicTo = prevTopic.to.filter(t => t !== connectorName);
    const transformer = g => ({ ...g, to: prevTopicTo });
    const updatedGraph = updateSingleGraph(graph, prevTopic.name, transformer);

    return updatedGraph;
  }

  // if there's no update, return the graph
  return graph;
};

export const updateGraph = params => {
  const {
    graph,
    update,
    isFromTopic,
    streamAppName = null,
    sinkName = null,
    dispatcher,
  } = params;

  let updatedGraph;

  // From topic update -- sink connectors or the From topic field update in stream apps
  if (isFromTopic) {
    const connectorName = sinkName || streamAppName;
    updatedGraph = cleanPrevFromTopics(graph, connectorName);

    // Update current topic
    const toTransformer = g => ({ ...g, to: update.to });
    updatedGraph = updateSingleGraph(updatedGraph, update.name, toTransformer);
  } else {
    const target = graph.find(g => g.name === update.name);

    // Adds the connector to graph
    if (isEmpty(target)) {
      updatedGraph = [...graph, update];
    } else {
      // Updates the target connector
      const transformer = g => ({ ...g, ...update });
      updatedGraph = updateSingleGraph(graph, target.name, transformer);
    }
  }

  // 1. Update active graph, this state is not kept on the server, so
  // we're storing it in the local state, and update if needed for now
  // 2. The update coming from toolbar is ommited since it will create
  // a weird effect the graph (set active state to the added graph and switch back
  // to the current active connecto)
  if (dispatcher.name !== 'TOOLBAR') {
    updatedGraph = updatedGraph.map(graph => {
      return { ...graph, isActive: graph.name === update.name };
    });
  }

  return updatedGraph;
};

export const loadGraph = (pipeline, currentConnectorName) => {
  const { objects, flows } = pipeline;

  const graph = flows.map((flow, index) => {
    const target = objects.find(object => object.name === flow.from.name);

    // Target object is not found, return a `null` value so
    // it can be fitler out later
    if (!target) return null;

    const { kind } = target;
    const isActive = flow.from.name === currentConnectorName;

    const props = {
      ...target,
      isActive,
      to: flows[index].to,
    };

    if (kind === 'topic' || kind === 'stream') {
      return {
        ...props,
        className: kind,
      };
    }

    return { ...props };
  });

  // Filter out `null` values since they're not valid to be rendered later in
  // the graph
  return graph.filter(Boolean);
};
