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

package com.island.ohara.agent.k8s

import com.island.ohara.agent.{BrokerCollie, NoSuchClusterException, NodeCollie, StreamCollie}
import com.island.ohara.client.configurator.v0.ContainerApi.ContainerInfo
import com.island.ohara.client.configurator.v0.FileInfoApi.FileInfo
import com.island.ohara.client.configurator.v0.NodeApi.Node
import com.island.ohara.client.configurator.v0.StreamApi.StreamClusterInfo
import com.island.ohara.streams.config.StreamDefUtils
import com.typesafe.scalalogging.Logger

import scala.concurrent.{ExecutionContext, Future}

private class K8SStreamCollieImpl(node: NodeCollie, bkCollie: BrokerCollie, k8sClient: K8SClient)
    extends K8SBasicCollieImpl[StreamClusterInfo](node, k8sClient)
    with StreamCollie {
  private[this] val LOG = Logger(classOf[K8SStreamCollieImpl])

  override protected def doCreator(executionContext: ExecutionContext,
                                   containerName: String,
                                   containerInfo: ContainerInfo,
                                   node: Node,
                                   route: Map[String, String],
                                   jmxPort: Int,
                                   jarInfo: FileInfo): Future[Unit] = {
    implicit val exec: ExecutionContext = executionContext
    k8sClient
      .containerCreator()
      .imageName(containerInfo.imageName)
      .nodeName(containerInfo.nodeName)
      .hostname(s"${containerInfo.name}$DIVIDER${node.name}")
      .name(containerInfo.name)
      .labelName(OHARA_LABEL)
      .domainName(K8S_DOMAIN_NAME)
      .args(StreamCollie.formatJMXProperties(node.name, jmxPort) ++
        Seq(StreamCollie.MAIN_ENTRY,
            s"""${StreamDefUtils.JAR_URL_DEFINITION.key()}=${jarInfo.url.toURI.toASCIIString}"""))
      .threadPool(executionContext)
      .create()
      .recover {
        case e: Throwable =>
          LOG.error(s"failed to start ${containerInfo.imageName} on ${node.name}", e)
          None
      }
      .map(_ => Unit)
  }

  override protected def toClusterDescription(clusterName: String, containers: Seq[ContainerInfo])(
    implicit executionContext: ExecutionContext): Future[StreamClusterInfo] =
    toStreamCluster(clusterName, containers)

  override protected def doRemoveNode(previousCluster: StreamClusterInfo, beRemovedContainer: ContainerInfo)(
    implicit executionContext: ExecutionContext): Future[Boolean] =
    Future.failed(
      new UnsupportedOperationException("stream collie doesn't support to remove node from a running cluster"))

  override protected def doAddNode(
    previousCluster: StreamClusterInfo,
    previousContainers: Seq[ContainerInfo],
    newNodeName: String)(implicit executionContext: ExecutionContext): Future[StreamClusterInfo] =
    Future.failed(new UnsupportedOperationException("stream collie doesn't support to add node from a running cluster"))

  override protected def doRunContainer(node: Node, containerInfo: ContainerInfo, commands: Seq[String])(
    implicit executionContext: ExecutionContext): Future[Option[String]] = {
    k8sClient
      .containerCreator()
      .imageName(containerInfo.imageName)
      .nodeName(containerInfo.nodeName)
      .hostname(s"${containerInfo.name}$DIVIDER${node.name}")
      .name(containerInfo.name)
      .labelName(OHARA_LABEL)
      .domainName(K8S_DOMAIN_NAME)
      .envs(containerInfo.environments)
      .args(commands)
      .threadPool(executionContext)
      .create()
      .flatMap(_ => k8sClient.log(containerInfo.name).map(str => Some(str)))
      .recover {
        case e: Throwable =>
          LOG.error(s"failed to run command for image ${containerInfo.imageName} on ${node.name}", e)
          None
      }
  }

  override protected def nodeCollie: NodeCollie = node
  override protected def prefixKey: String = PREFIX_KEY

  override protected def brokerContainers(clusterName: String)(
    implicit executionContext: ExecutionContext): Future[Seq[ContainerInfo]] =
    bkCollie
      .clusters()
      .map(
        _.find(_._1.name == clusterName)
          .map(_._2)
          .getOrElse(throw new NoSuchClusterException(s"broker cluster:$clusterName does not exist")))
}
