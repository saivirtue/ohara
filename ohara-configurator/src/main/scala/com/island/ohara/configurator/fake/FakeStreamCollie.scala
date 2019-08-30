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

package com.island.ohara.configurator.fake

import com.island.ohara.agent.{ClusterState, NodeCollie, StreamCollie}
import com.island.ohara.client.configurator.v0.ContainerApi.ContainerInfo
import com.island.ohara.client.configurator.v0.DefinitionApi.Params
import com.island.ohara.client.configurator.v0.FileInfoApi.FileInfo
import com.island.ohara.client.configurator.v0.MetricsApi.Metrics
import com.island.ohara.client.configurator.v0.NodeApi.Node
import com.island.ohara.client.configurator.v0.StreamApi
import com.island.ohara.client.configurator.v0.StreamApi.StreamClusterInfo
import com.island.ohara.common.setting.Definition
import com.island.ohara.common.util.CommonUtils
import com.island.ohara.configurator.route.StreamRoute
import com.island.ohara.metrics.basic.{Counter, CounterMBean}
import com.island.ohara.streams.StreamApp
import com.island.ohara.streams.config.StreamDefUtils

import scala.concurrent.{ExecutionContext, Future}

private[configurator] class FakeStreamCollie(node: NodeCollie)
    extends FakeCollie[StreamClusterInfo](node)
    with StreamCollie {

  override def counters(cluster: StreamClusterInfo): Seq[CounterMBean] =
    // we fake counters since streamApp is not really running in fake collie mode
    Seq(
      Counter
        .builder()
        .group(StreamRoute.STREAM_APP_GROUP)
        .name("fakeCounter")
        .value(CommonUtils.randomInteger().toLong)
        .build())

  override def creator: StreamCollie.ClusterCreator =
    (_, nodeNames, _, _, _, _, _, _, settings, _) =>
      Future.successful(
        addCluster(
          StreamApi.StreamClusterInfo(
            settings = settings,
            // convert to list in order to be serializable
            nodeNames = nodeNames,
            deadNodes = Set.empty,
            // In fake mode, we need to assign a state in creation for "GET" method to act like real case
            state = Some(ClusterState.RUNNING.name),
            error = None,
            metrics = Metrics.EMPTY,
            lastModified = CommonUtils.current()
          )))

  override protected def doRemoveNode(previousCluster: StreamClusterInfo, beRemovedContainer: ContainerInfo)(
    implicit executionContext: ExecutionContext): Future[Boolean] =
    Future.failed(
      new UnsupportedOperationException("stream collie doesn't support to remove node from a running cluster"))

  override protected def doAddNode(
    previousCluster: StreamClusterInfo,
    previousContainers: Seq[ContainerInfo],
    newNodeName: String)(implicit executionContext: ExecutionContext): Future[StreamClusterInfo] =
    Future.failed(new UnsupportedOperationException("stream collie doesn't support to add node from a running cluster"))

  override protected def doCreator(executionContext: ExecutionContext,
                                   containerName: String,
                                   containerInfo: ContainerInfo,
                                   node: Node,
                                   route: Map[String, String],
                                   jmxPort: Int,
                                   jarInfo: FileInfo): Future[Unit] = Future.unit

  override protected def nodeCollie: NodeCollie = node

  override protected def prefixKey: String = "fakestream"

  // in fake mode, we never return empty result or exception.
  override def fetchDefinitions(params: Params)(implicit executionContext: ExecutionContext,
                                                nodeCollie: NodeCollie): Future[Definition] =
    Future.successful {
      try {
        import sys.process._
        val classpath = System.getProperty("java.class.path")
        val command =
          s"""java -cp "$classpath" ${StreamCollie.MAIN_ENTRY} ${StreamDefUtils.JAR_URL_DEFINITION.key()}="${params.jarInfo.fold(
            throw new Exception("jar is empty"))(_.url.toURI.toASCIIString)}" ${StreamApp.DEFINITION_COMMAND}"""
        val result = command.!!
        Definition.ofJson(result)
      } catch {
        case _: Throwable =>
          Definition.of("fake_class", StreamDefUtils.DEFAULT) // a serializable collection
      }
    }

  override protected def doRunContainer(node: Node, containerInfo: ContainerInfo, commands: Seq[String])(
    implicit executionContext: ExecutionContext): Future[Option[String]] =
    throw new UnsupportedOperationException

  override protected def brokerContainers(clusterName: String)(
    implicit executionContext: ExecutionContext): Future[Seq[ContainerInfo]] =
    throw new UnsupportedOperationException
}
