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

import java.util.concurrent.ConcurrentHashMap

import com.island.ohara.agent.{BrokerCollie, ClusterState, NoSuchClusterException, NodeCollie}
import com.island.ohara.client.configurator.v0.BrokerApi.BrokerClusterInfo
import com.island.ohara.client.configurator.v0.ContainerApi.ContainerInfo
import com.island.ohara.client.configurator.v0.{BrokerApi, NodeApi, TopicApi}
import com.island.ohara.client.kafka.TopicAdmin
import com.island.ohara.common.setting.ObjectKey
import com.island.ohara.common.util.CommonUtils
import com.island.ohara.metrics.BeanChannel
import com.island.ohara.metrics.kafka.TopicMeter

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

private[configurator] class FakeBrokerCollie(node: NodeCollie, bkConnectionProps: String)
    extends FakeCollie[BrokerClusterInfo](node)
    with BrokerCollie {

  override def topicMeters(cluster: BrokerClusterInfo): Seq[TopicMeter] =
    // we don't care for the fake mode since both fake mode and embedded mode are run on local jvm
    BeanChannel.local().topicMeters().asScala

  /**
    * cache all topics info in-memory so we should keep instance for each fake cluster.
    */
  private[this] val fakeAdminCache = new ConcurrentHashMap[BrokerClusterInfo, FakeTopicAdmin]

  override def creator: BrokerCollie.ClusterCreator = (_, creation) =>
    Future.successful(
      addCluster(
        BrokerClusterInfo(
          settings = BrokerApi.access.request
            .settings(creation.settings)
            .nodeNames(creation.nodeNames ++ clusterCache.asScala
              .find(_._1.key == creation.key)
              .map(_._1.nodeNames)
              .getOrElse(Set.empty))
            .creation
            .settings,
          aliveNodes = creation.nodeNames ++ clusterCache.asScala
            .find(_._1.key == creation.key)
            .map(_._1.nodeNames)
            .getOrElse(Set.empty),
          // In fake mode, we need to assign a state in creation for "GET" method to act like real case
          state = Some(ClusterState.RUNNING.name),
          error = None,
          lastModified = CommonUtils.current(),
          topicSettingDefinitions = TopicApi.TOPIC_DEFINITIONS
        )))

  override protected def doRemoveNode(previousCluster: BrokerClusterInfo, beRemovedContainer: ContainerInfo)(
    implicit executionContext: ExecutionContext): Future[Boolean] = Future
    .successful(
      addCluster(
        previousCluster
          .newNodeNames(previousCluster.nodeNames.filterNot(_ == beRemovedContainer.nodeName))
          .asInstanceOf[BrokerClusterInfo]))
    .map(_ => true)

  override def topicAdmin(cluster: BrokerClusterInfo): TopicAdmin =
    if (bkConnectionProps == null) {
      if (!clusterCache.containsKey(cluster))
        throw new NoSuchClusterException(s"cluster:${cluster.name} is not running")
      val fake = new FakeTopicAdmin
      val r = fakeAdminCache.putIfAbsent(cluster, fake)
      if (r == null) fake else r
    } else TopicAdmin(bkConnectionProps)

  override protected def doCreator(executionContext: ExecutionContext,
                                   containerName: String,
                                   containerInfo: ContainerInfo,
                                   node: NodeApi.Node,
                                   route: Map[String, String]): Future[Unit] =
    throw new UnsupportedOperationException("Fake broker collie doesn't support doCreator function")

  protected override def zookeeperContainers(zkClusterKey: ObjectKey)(
    implicit executionContext: ExecutionContext): Future[Seq[ContainerInfo]] =
    throw new UnsupportedOperationException("Fake broker doesn't support zookeeperCluster function")

  /**
    * Please setting nodeCollie to implement class
    *
    * @return
    */
  override protected def nodeCollie: NodeCollie = node

  /**
    * Implement prefix name for the platform
    *
    * @return
    */
  override protected def prefixKey: String = "fakebroker"
}
