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

package com.island.ohara.client.configurator.v0

import com.island.ohara.client.configurator.Data

/**
  * There are many kinds of cluster hosted by ohara. We extract an interface to define "what" information should be included by a "cluster
  * information".
  */
trait ClusterInfo extends Data {

  /**
    * @return cluster name
    */
  def name: String

  /**
    * @return docker image name used to build container for this cluster
    */
  def imageName: String

  /**
    * All services hosted by ohara should use some ports, which are used to communicate.
    * We "highlight" this method since port checking is a important thing for configurator
    * @return ports used by this cluster
    */
  def ports: Set[Int]

  /**
    * @return nodes running this cluster
    */
  def nodeNames: Set[String]

  /**
    * List the dead nodes.
    * This is the counting "unit" for this cluster ; dead nodes have no relation with other clusters.
    *
    * @return nodes which the rely container was dead
    */
  def deadNodes: Set[String]

  /**
    * Create an new instance with new node names.
    * @param newNodeNames new node names
    * @return an new instance
    */
  def clone(newNodeNames: Set[String]): ClusterInfo
}
