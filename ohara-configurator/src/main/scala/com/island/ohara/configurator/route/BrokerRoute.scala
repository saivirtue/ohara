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
import com.island.ohara.agent.{BrokerCollie, ClusterCollie, NodeCollie, ZookeeperCollie}
import com.island.ohara.client.configurator.v0.BrokerApi.{Creation, _}
import com.island.ohara.client.configurator.v0.{BrokerApi, TopicApi}
import com.island.ohara.common.setting.ObjectKey
import com.island.ohara.common.util.CommonUtils
import com.island.ohara.configurator.route.hook.{HookOfAction, HookOfCreation, HookOfUpdating}
import com.island.ohara.configurator.store.{DataStore, MeterCache}

import scala.concurrent.{ExecutionContext, Future}
object BrokerRoute {

  private[this] def hookOfCreation(implicit zookeeperCollie: ZookeeperCollie,
                                   executionContext: ExecutionContext): HookOfCreation[Creation, BrokerClusterInfo] =
    (creation: Creation) =>
      creation.zookeeperClusterKey.map(Future.successful).getOrElse(CollieUtils.singleZookeeperCluster()).map { zkKey =>
        BrokerClusterInfo(
          settings = BrokerApi.access.request.settings(creation.settings).zookeeperClusterKey(zkKey).creation.settings,
          aliveNodes = Set.empty,
          state = None,
          error = None,
          lastModified = CommonUtils.current(),
          topicSettingDefinitions = TopicApi.TOPIC_DEFINITIONS
        )
    }

  private[this] def HookOfUpdating(
    implicit zookeeperCollie: ZookeeperCollie,
    clusterCollie: ClusterCollie,
    executionContext: ExecutionContext): HookOfUpdating[Creation, Updating, BrokerClusterInfo] =
    (key: ObjectKey, update: Updating, previousOption: Option[BrokerClusterInfo]) =>
      clusterCollie.brokerCollie
        .clusters()
        .flatMap { clusters =>
          if (clusters.keys.filter(_.key == key).exists(_.state.nonEmpty))
            throw new RuntimeException(s"You cannot update property on non-stopped broker cluster: $key")
          update.zookeeperClusterKey
            .orElse(previousOption.map(_.zookeeperClusterKey))
            .map(Future.successful)
            .getOrElse(CollieUtils.singleZookeeperCluster())
        }
        .map { zkKey =>
          // 1) fill the previous settings (if exists)
          // 2) overwrite previous settings by updated settings
          // 3) fill the ignored settings by creation
          val newSettings = previousOption.map(_.settings).getOrElse(Map.empty) ++ update.settings
          BrokerClusterInfo(
            settings = BrokerApi.access.request.settings(newSettings).zookeeperClusterKey(zkKey).creation.settings,
            aliveNodes = Set.empty,
            state = None,
            error = None,
            lastModified = CommonUtils.current(),
            topicSettingDefinitions = TopicApi.TOPIC_DEFINITIONS
          )
      }

  private[this] def hookOfStart(implicit store: DataStore,
                                clusterCollie: ClusterCollie,
                                executionContext: ExecutionContext): HookOfAction =
    (key: ObjectKey, _, _) =>
      store
        .value[BrokerClusterInfo](key)
        .flatMap(brokerClusterInfo => clusterCollie.clusters().map(_.keys.toSeq).map(_ -> brokerClusterInfo))
        .flatMap {
          case (clusters, brokerClusterInfo) =>
            val sameZkNameClusters = clusters
              .filter(_.isInstanceOf[BrokerClusterInfo])
              .map(_.asInstanceOf[BrokerClusterInfo])
              .filter(_.zookeeperClusterKey == brokerClusterInfo.zookeeperClusterKey)
            if (sameZkNameClusters.nonEmpty)
              throw new IllegalArgumentException(
                s"zk cluster:${brokerClusterInfo.zookeeperClusterKey} is already used by broker cluster:${sameZkNameClusters.head.name}")
            clusterCollie.brokerCollie.creator
              .settings(brokerClusterInfo.settings)
              .name(brokerClusterInfo.name)
              .group(brokerClusterInfo.group)
              .clientPort(brokerClusterInfo.clientPort)
              .exporterPort(brokerClusterInfo.exporterPort)
              .jmxPort(brokerClusterInfo.jmxPort)
              .zookeeperClusterKey(brokerClusterInfo.zookeeperClusterKey)
              .imageName(brokerClusterInfo.imageName)
              .nodeNames(brokerClusterInfo.nodeNames)
              .threadPool(executionContext)
              .create()
        }
        .map(_ => Unit)

  private[this] def hookBeforeStop(implicit store: DataStore,
                                   clusterCollie: ClusterCollie,
                                   executionContext: ExecutionContext): HookOfAction =
    (key: ObjectKey, _: String, _: Map[String, String]) =>
      store
        .value[BrokerClusterInfo](key)
        .flatMap(
          brokerClusterInfo =>
            clusterCollie.workerCollie
              .clusters()
              .map(
                _.keys
                  .find(_.brokerClusterName == brokerClusterInfo.name)
                  .map(cluster =>
                    throw new IllegalArgumentException(
                      s"you can't remove broker cluster:${brokerClusterInfo.name} since it is used by worker cluster:${cluster.name}"))
            ))

  def apply(implicit store: DataStore,
            meterCache: MeterCache,
            zookeeperCollie: ZookeeperCollie,
            brokerCollie: BrokerCollie,
            clusterCollie: ClusterCollie,
            nodeCollie: NodeCollie,
            executionContext: ExecutionContext): server.Route =
    clusterRoute(
      root = BROKER_PREFIX_PATH,
      metricsKey = None,
      hookOfCreation = hookOfCreation,
      HookOfUpdating = HookOfUpdating,
      hookOfStart = hookOfStart,
      hookBeforeStop = hookBeforeStop
    )
}
