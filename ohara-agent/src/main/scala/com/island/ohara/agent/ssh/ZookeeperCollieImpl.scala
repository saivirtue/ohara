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

import com.island.ohara.agent.{ClusterCache, NodeCollie, ZookeeperCollie}
import com.island.ohara.client.configurator.v0.ClusterInfo
import com.island.ohara.client.configurator.v0.ContainerApi.ContainerInfo
import com.island.ohara.client.configurator.v0.NodeApi.Node
import com.island.ohara.client.configurator.v0.ZookeeperApi.ZookeeperClusterInfo
import scala.concurrent.{ExecutionContext, Future}
private class ZookeeperCollieImpl(node: NodeCollie, dockerCache: DockerClientCache, clusterCache: ClusterCache)
    extends BasicCollieImpl[ZookeeperClusterInfo, ZookeeperCollie.ClusterCreator](node, dockerCache, clusterCache)
    with ZookeeperCollie {

  override protected def doCreator(executionContext: ExecutionContext,
                                   clusterName: String,
                                   containerName: String,
                                   containerInfo: ContainerInfo,
                                   node: Node,
                                   route: Map[String, String]): Future[Unit] =
    Future.successful(try {
      dockerCache.exec(
        node,
        _.containerCreator()
          .imageName(containerInfo.imageName)
          .portMappings(
            containerInfo.portMappings.flatMap(_.portPairs).map(pair => pair.hostPort -> pair.containerPort).toMap)
          .hostname(containerInfo.hostname)
          .envs(containerInfo.environments)
          .name(containerInfo.name)
          .route(route)
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

  override protected def postCreateZookeeperCluster(clusterInfo: ClusterInfo,
                                                    successfulContainers: Seq[ContainerInfo]): Unit = {
    clusterCache.put(clusterInfo, clusterCache.get(clusterInfo) ++ successfulContainers)
  }

  override protected def doRemoveNode(previousCluster: ZookeeperClusterInfo, beRemovedContainer: ContainerInfo)(
    implicit executionContext: ExecutionContext): Future[Boolean] =
    Future.failed(
      new UnsupportedOperationException("zookeeper collie doesn't support remove node from a running cluster"))

  override protected def doAddNode(
    previousCluster: ZookeeperClusterInfo,
    previousContainers: Seq[ContainerInfo],
    newNodeName: String)(implicit executionContext: ExecutionContext): Future[ZookeeperClusterInfo] =
    Future.failed(new UnsupportedOperationException("zookeeper collie doesn't support add node from a running cluster"))

  override protected def nodeCollie: NodeCollie = node

  override protected def prefixKey: String = PREFIX_KEY
}
