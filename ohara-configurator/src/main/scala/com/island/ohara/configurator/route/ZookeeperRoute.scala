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

package com.island.ohara.configurator.route

import akka.http.scaladsl.server
import com.island.ohara.agent.{ClusterCollie, NodeCollie, ZookeeperCollie}
import com.island.ohara.client.configurator.v0.ZookeeperApi._
import com.island.ohara.common.setting.ObjectKey
import com.island.ohara.common.util.CommonUtils
import com.island.ohara.configurator.route.hook.{HookOfAction, HookOfCreation, HookOfUpdating}
import com.island.ohara.configurator.store.{DataStore, MeterCache}

import scala.concurrent.{ExecutionContext, Future}

object ZookeeperRoute {

  private[this] def hookOfCreation: HookOfCreation[Creation, ZookeeperClusterInfo] = (creation: Creation) =>
    Future.successful(
      ZookeeperClusterInfo(
        settings = creation.settings,
        aliveNodes = Set.empty,
        state = None,
        error = None,
        lastModified = CommonUtils.current()
      ))

  private[this] def HookOfUpdating(
    implicit clusterCollie: ClusterCollie,
    executionContext: ExecutionContext): HookOfUpdating[Creation, Updating, ZookeeperClusterInfo] =
    (key: ObjectKey, update: Updating, previousOption: Option[ZookeeperClusterInfo]) =>
      clusterCollie.zookeeperCollie.clusters().map { clusters =>
        if (clusters.keys.filter(_.key == key).exists(_.state.nonEmpty))
          throw new RuntimeException(s"You cannot update property on non-stopped zookeeper cluster: $key")
        else
          // use PUT as creation request
          ZookeeperClusterInfo(
            // 1) fill the previous settings (if exists)
            // 2) overwrite previous settings by updated settings
            // 3) fill the ignored settings by creation
            settings = access.request
              .settings(previousOption.map(_.settings).getOrElse(Map.empty))
              .settings(update.settings)
              .creation
              .settings,
            // this cluster is not running so we don't need to keep the dead nodes in the updated cluster.
            aliveNodes = Set.empty,
            state = None,
            error = None,
            lastModified = CommonUtils.current()
          )
    }

  private[this] def hookOfStart(implicit store: DataStore,
                                clusterCollie: ClusterCollie,
                                executionContext: ExecutionContext): HookOfAction =
    (key: ObjectKey, _, _) =>
      store
        .value[ZookeeperClusterInfo](key)
        .flatMap(
          zkClusterInfo =>
            clusterCollie.zookeeperCollie.creator
              .settings(zkClusterInfo.settings)
              .name(zkClusterInfo.name)
              .group(zkClusterInfo.group)
              .clientPort(zkClusterInfo.clientPort)
              .electionPort(zkClusterInfo.electionPort)
              .peerPort(zkClusterInfo.peerPort)
              .imageName(zkClusterInfo.imageName)
              .nodeNames(zkClusterInfo.nodeNames)
              .threadPool(executionContext)
              .create())
        .map(_ => Unit)

  private[this] def hookBeforeStop(
    implicit store: DataStore,
    clusterCollie: ClusterCollie,
    executionContext: ExecutionContext): HookOfAction = (key: ObjectKey, _: String, _: Map[String, String]) =>
    store
      .value[ZookeeperClusterInfo](key)
      .flatMap(zkClusterInfo =>
        clusterCollie.brokerCollie
          .clusters()
          .map(
            _.keys
              .find(_.zookeeperClusterKey == zkClusterInfo.key)
              .map(cluster =>
                throw new IllegalArgumentException(
                  s"you can't remove zookeeper cluster:${zkClusterInfo.key} since it is used by broker cluster:${cluster.name}"))
        ))

  def apply(implicit store: DataStore,
            meterCache: MeterCache,
            zookeeperCollie: ZookeeperCollie,
            clusterCollie: ClusterCollie,
            nodeCollie: NodeCollie,
            executionContext: ExecutionContext): server.Route =
    clusterRoute(
      root = ZOOKEEPER_PREFIX_PATH,
      metricsKey = None,
      hookOfCreation = hookOfCreation,
      HookOfUpdating = HookOfUpdating,
      hookOfStart = hookOfStart,
      hookBeforeStop = hookBeforeStop
    )
}
