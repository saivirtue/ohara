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

package com.island.ohara.agent.ssh

import java.net.URL

import com.island.ohara.agent.{ClusterCache, NodeCollie, StreamCollie}
import com.island.ohara.client.configurator.v0.ClusterInfo
import com.island.ohara.client.configurator.v0.ContainerApi.ContainerInfo
import com.island.ohara.client.configurator.v0.NodeApi.Node
import com.island.ohara.client.configurator.v0.StreamApi.StreamClusterInfo
import com.island.ohara.streams.config.StreamDefinitions.DefaultConfigs

import scala.concurrent.{ExecutionContext, Future}
private class StreamCollieImpl(node: NodeCollie, dockerCache: DockerClientCache, clusterCache: ClusterCache)
    extends BasicCollieImpl[StreamClusterInfo, StreamCollie.ClusterCreator](node, dockerCache, clusterCache)
    with StreamCollie {

  override protected def doCreator(executionContext: ExecutionContext,
                                   containerName: String,
                                   containerInfo: ContainerInfo,
                                   node: Node,
                                   route: Map[String, String],
                                   jmxPort: Int,
                                   jarUrl: URL): Future[Unit] =
    Future.successful(try {
      dockerCache.exec(
        node,
        _.containerCreator()
          .imageName(containerInfo.imageName)
          .hostname(containerInfo.name)
          .envs(escapeQuote(containerInfo.environments))
          .name(containerInfo.name)
          .portMappings(
            containerInfo.portMappings.flatMap(_.portPairs).map(pair => pair.hostPort -> pair.containerPort).toMap)
          .route(route)
          .command(String.join(
            " ",
            StreamCollie.formatJMXProperties(node.name, jmxPort).mkString(" "),
            StreamCollie.MAIN_ENTRY,
            s"""${DefaultConfigs.JAR_KEY_DEFINITION.key()}=${StreamCollie.urlEncode(jarUrl)}"""
          ))
          .create()
      )
    } catch {
      case e: Throwable =>
        try dockerCache.exec(node, _.forceRemove(containerName))
        catch {
          case _: Throwable =>
          // do nothing
        }
        LOG.error(s"failed to start ${containerInfo.imageName} on ${node.name}", e)
        None
    })

  override protected def postCreateCluster(clusterInfo: ClusterInfo, successfulContainers: Seq[ContainerInfo]): Unit =
    clusterCache.put(clusterInfo, clusterCache.get(clusterInfo) ++ successfulContainers)

  override protected def doRemoveNode(previousCluster: StreamClusterInfo, beRemovedContainer: ContainerInfo)(
    implicit executionContext: ExecutionContext): Future[Boolean] =
    Future.failed(new UnsupportedOperationException("stream collie doesn't support remove node from a running cluster"))

  override protected def doAddNode(
    previousCluster: StreamClusterInfo,
    previousContainers: Seq[ContainerInfo],
    newNodeName: String)(implicit executionContext: ExecutionContext): Future[StreamClusterInfo] =
    Future.failed(new UnsupportedOperationException("stream collie doesn't support add node from a running cluster"))

  override protected def nodeCollie: NodeCollie = node
  override protected def prefixKey: String = PREFIX_KEY

  /**
    * Since docker will "swallow" quote of environment variable in container
    * We need to escape the special quote (like json string) here.
    * @param envs environment
    * @return the escape environment
    */
  private[this] def escapeQuote(envs: Map[String, String]): Map[String, String] = {
    envs.map { case (k, v) => (k, v.replaceAll("\"", "\\\\\"")) }
  }
}
