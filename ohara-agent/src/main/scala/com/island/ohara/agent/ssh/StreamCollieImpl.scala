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

import com.island.ohara.agent.{ClusterCache, NoSuchClusterException, NodeCollie, StreamCollie}
import com.island.ohara.client.configurator.v0.BrokerApi.BrokerClusterInfo
import com.island.ohara.client.configurator.v0.ClusterInfo
import com.island.ohara.client.configurator.v0.ContainerApi.ContainerInfo
import com.island.ohara.client.configurator.v0.FileInfoApi.FileInfo
import com.island.ohara.client.configurator.v0.NodeApi.Node
import com.island.ohara.client.configurator.v0.StreamApi.StreamClusterInfo
import com.island.ohara.streams.config.StreamDefUtils

import scala.concurrent.{ExecutionContext, Future}
private class StreamCollieImpl(node: NodeCollie, dockerCache: DockerClientCache, clusterCache: ClusterCache)
    extends BasicCollieImpl[StreamClusterInfo](node, dockerCache, clusterCache)
    with StreamCollie {

  override protected def doCreator(executionContext: ExecutionContext,
                                   containerName: String,
                                   containerInfo: ContainerInfo,
                                   node: Node,
                                   route: Map[String, String],
                                   jmxPort: Int,
                                   jarInfo: FileInfo): Future[Unit] =
    Future.successful(try {
      dockerCache.exec(
        node,
        _.containerCreator()
          .imageName(containerInfo.imageName)
          .hostname(containerInfo.name)
          .envs(containerInfo.environments)
          .name(containerInfo.name)
          .portMappings(
            containerInfo.portMappings.flatMap(_.portPairs).map(pair => pair.hostPort -> pair.containerPort).toMap)
          .route(route)
          .command(String.join(
            " ",
            StreamCollie.formatJMXProperties(node.name, jmxPort).mkString(" "),
            StreamCollie.MAIN_ENTRY,
            s"""${StreamDefUtils.JAR_URL_DEFINITION.key()}="${jarInfo.url.toURI.toASCIIString}""""
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

  override protected def doRunContainer(node: Node, containerInfo: ContainerInfo, commands: Seq[String])(
    implicit executionContext: ExecutionContext): Future[Option[String]] =
    Future.successful(try {
      dockerCache.exec(
        node,
        _.containerCreator()
          .imageName(containerInfo.imageName)
          .hostname(containerInfo.name)
          .name(containerInfo.name)
          .command(commands.mkString(" "))
          .create()
      )
      Some(dockerCache.exec(node, _.log(containerInfo.name)))
    } catch {
      case e: Throwable =>
        LOG.error(s"failed to run command for image ${containerInfo.imageName} on ${node.name}", e)
        None
    } finally {
      // after fetch the console string, remove the container immediately
      try dockerCache.exec(node, _.forceRemove(containerInfo.name))
      catch {
        case _: Throwable =>
        // do nothing
      }
    })

  override protected def nodeCollie: NodeCollie = node
  override protected def prefixKey: String = PREFIX_KEY

  override protected def brokerContainers(clusterName: String)(
    implicit executionContext: ExecutionContext): Future[Seq[ContainerInfo]] =
    Future.successful(
      clusterCache.snapshot
        .filter(_._1.isInstanceOf[BrokerClusterInfo])
        .find(_._1.name == clusterName)
        .map(_._2)
        .getOrElse(
          throw new NoSuchClusterException(s"broker cluster:$clusterName doesn't exist. other broker clusters")))
}
