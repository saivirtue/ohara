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

package com.island.ohara.agent
import com.island.ohara.client.configurator.v0.BrokerApi.BrokerClusterInfo
import com.island.ohara.client.configurator.v0.ContainerApi.ContainerInfo
import com.island.ohara.client.configurator.v0.NodeApi.Node
import com.island.ohara.client.configurator.v0._
import com.island.ohara.common.setting.ObjectKey
import com.island.ohara.common.util.CommonUtils

import scala.concurrent.{ExecutionContext, Future}
private class FakeBrokerCollie(nodes: Seq[Node],
                               zkContainers: Seq[ContainerInfo],
                               bkExistContainers: Seq[ContainerInfo])
    extends BrokerCollie {
  protected override def zookeeperContainers(zkClusterKey: ObjectKey)(
    implicit executionContext: ExecutionContext): Future[Seq[ContainerInfo]] =
    if (FakeBrokerCollie.zookeeperClusterKey == zkClusterKey) Future.successful(zkContainers)
    else Future.failed(new NoSuchClusterException(s"$zkClusterKey does not exist"))

  override protected def doCreator(executionContext: ExecutionContext,
                                   containerName: String,
                                   containerInfo: ContainerApi.ContainerInfo,
                                   node: NodeApi.Node,
                                   route: Map[String, String]): Future[Unit] = {
    // Nothing
    Future.unit
  }

  override protected def doRemove(clusterInfo: BrokerClusterInfo, containerInfos: Seq[ContainerInfo])(
    implicit executionContext: ExecutionContext): Future[Boolean] =
    throw new UnsupportedOperationException("Not support remove function")

  override def logs(objectKey: ObjectKey)(
    implicit executionContext: ExecutionContext): Future[Map[ContainerInfo, String]] =
    throw new UnsupportedOperationException("Not support logs function")

  override def clusterWithAllContainers()(
    implicit executionContext: ExecutionContext): Future[Map[BrokerClusterInfo, Seq[ContainerInfo]]] =
    Future.successful(
      //Pre create broker container for test
      Map(
        BrokerClusterInfo(
          settings = BrokerApi.access.request
            .name("bk1")
            .imageName(BrokerApi.IMAGE_NAME_DEFAULT)
            .zookeeperClusterKey(ObjectKey.of(GROUP_DEFAULT, "zk1"))
            .nodeNames(nodes.map(_.hostname).toSet)
            .creation
            .settings,
          aliveNodes = nodes.map(_.hostname).toSet,
          lastModified = CommonUtils.current(),
          state = None,
          error = None,
          topicSettingDefinitions = TopicApi.TOPIC_DEFINITIONS
        ) -> bkExistContainers)
    )

  override protected def resolveHostName(node: String): String = "1.1.1.1"

  override protected def doRemoveNode(previousCluster: BrokerClusterInfo, beRemovedContainer: ContainerInfo)(
    implicit executionContext: ExecutionContext): Future[Boolean] =
    throw new UnsupportedOperationException("Not support removeNode function")

  /**
    * Please setting nodeCollie to implement class
    *
    * @return
    */
  override protected def nodeCollie: NodeCollie = NodeCollie(nodes)

  /**
    * Implement prefix name for the platform
    *
    * @return
    */
  override protected def prefixKey: String = "fakebroker"

  // In fake mode, we don't care the cluster state
  override protected def toClusterState(containers: Seq[ContainerInfo]): Option[ClusterState] =
    Some(ClusterState.RUNNING)
}

object FakeBrokerCollie {
  val zookeeperClusterKey: ObjectKey = ObjectKey.of(GROUP_DEFAULT, "zk1")
}
