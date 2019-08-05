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
import com.island.ohara.client.configurator.v0.BrokerApi.{Creation, _}
import com.island.ohara.client.configurator.v0.TopicApi
import com.island.ohara.common.setting.ObjectKey
import com.island.ohara.common.util.CommonUtils
import com.island.ohara.configurator.route.RouteUtils._
import com.island.ohara.configurator.store.DataStore

import scala.concurrent.{ExecutionContext, Future}
object BrokerRoute {

  private[this] def updateState(info: BrokerClusterInfo)(
    implicit store: DataStore,
    clusterCollie: ClusterCollie,
    executionContext: ExecutionContext): Future[BrokerClusterInfo] =
    store
      .value[BrokerClusterInfo](info.key)
      .flatMap(
        cluster =>
          clusterCollie.brokerCollie
            .exist(cluster.name)
            .flatMap {
              if (_) {
                clusterCollie.brokerCollie.cluster(cluster.name).map(_._1)
              } else {
                // if broker cluster was not created, we initial state and error
                Future.successful(cluster.copy(state = None, error = None))
              }
            }
            .flatMap { finalData =>
              store.addIfPresent[BrokerClusterInfo](
                key = info.key,
                updater = (previous: BrokerClusterInfo) =>
                  previous.copy(
                    zookeeperClusterName = finalData.zookeeperClusterName,
                    state = finalData.state,
                    nodeNames = finalData.nodeNames,
                    deadNodes = finalData.deadNodes,
                )
              )
          }
      )

  private[this] def hookOfGet(implicit store: DataStore,
                              clusterCollie: ClusterCollie,
                              executionContext: ExecutionContext): HookOfGet[BrokerClusterInfo] =
    updateState(_)

  private[this] def hookOfList(implicit store: DataStore,
                               clusterCollie: ClusterCollie,
                               executionContext: ExecutionContext): HookOfList[BrokerClusterInfo] =
    Future.traverse(_)(updateState)

  private[this] def hookOfCreation(implicit zookeeperCollie: ZookeeperCollie,
                                   executionContext: ExecutionContext): HookOfCreation[Creation, BrokerClusterInfo] =
    (creation: Creation) =>
      CollieUtils.orElseClusterName(creation.zookeeperClusterName).map { zkName =>
        BrokerClusterInfo(
          name = creation.name,
          imageName = creation.imageName,
          zookeeperClusterName = zkName,
          clientPort = creation.clientPort,
          exporterPort = creation.exporterPort,
          jmxPort = creation.jmxPort,
          nodeNames = creation.nodeNames,
          deadNodes = Set.empty,
          tags = creation.tags,
          state = None,
          error = None,
          lastModified = CommonUtils.current(),
          topicSettingDefinitions = TopicApi.TOPIC_DEFINITIONS
        )
    }

  private[this] def hookOfUpdate(
    implicit zookeeperCollie: ZookeeperCollie,
    clusterCollie: ClusterCollie,
    executionContext: ExecutionContext): HookOfUpdate[Creation, Update, BrokerClusterInfo] =
    (key: ObjectKey, update: Update, previous: Option[BrokerClusterInfo]) =>
      clusterCollie.brokerCollie
        .clusters()
        .flatMap { clusters =>
          if (clusters.keys.filter(_.name == key.name()).exists(_.state.nonEmpty))
            throw new RuntimeException(s"You cannot update property on non-stopped broker cluster: $key")
          CollieUtils.orElseClusterName(update.zookeeperClusterName.orElse(previous.map(_.zookeeperClusterName)))
        }
        .map { zkName =>
          previous.fold(
            BrokerClusterInfo(
              name = key.name,
              imageName = update.imageName.getOrElse(IMAGE_NAME_DEFAULT),
              zookeeperClusterName = zkName,
              clientPort = update.clientPort.getOrElse(CommonUtils.availablePort()),
              exporterPort = update.exporterPort.getOrElse(CommonUtils.availablePort()),
              jmxPort = update.jmxPort.getOrElse(CommonUtils.availablePort()),
              nodeNames = update.nodeNames.getOrElse(Set.empty),
              deadNodes = Set.empty,
              tags = update.tags.getOrElse(Map.empty),
              state = None,
              error = None,
              lastModified = CommonUtils.current(),
              topicSettingDefinitions = TopicApi.TOPIC_DEFINITIONS
            )) { previous =>
            previous.copy(
              imageName = update.imageName.getOrElse(previous.imageName),
              zookeeperClusterName = zkName,
              clientPort = update.clientPort.getOrElse(previous.clientPort),
              exporterPort = update.exporterPort.getOrElse(previous.exporterPort),
              jmxPort = update.jmxPort.getOrElse(previous.jmxPort),
              nodeNames = update.nodeNames.getOrElse(previous.nodeNames),
              tags = update.tags.getOrElse(previous.tags),
              lastModified = CommonUtils.current()
            )
          }
      }

  private[this] def hookBeforeDelete(implicit store: DataStore,
                                     clusterCollie: ClusterCollie,
                                     executionContext: ExecutionContext): HookBeforeDelete = (key: ObjectKey) =>
    store.get[BrokerClusterInfo](key).flatMap {
      _.fold(Future.unit) { info =>
        updateState(info).flatMap { data =>
          if (data.state.isEmpty) Future.unit
          else Future.failed(new RuntimeException(s"You cannot delete a non-stopped broker :$key"))
        }
      }
  }

  private[this] def hookOfStart(implicit store: DataStore,
                                clusterCollie: ClusterCollie,
                                executionContext: ExecutionContext): HookOfAction =
    (key: ObjectKey, _: String, _: Map[String, String]) =>
      store
        .value[BrokerClusterInfo](key)
        .flatMap(brokerClusterInfo => clusterCollie.clusters().map(_.keys.toSeq).map(_ -> brokerClusterInfo))
        .flatMap {
          case (clusters, brokerClusterInfo) =>
            val sameZkNameClusters = clusters
              .filter(_.isInstanceOf[BrokerClusterInfo])
              .map(_.asInstanceOf[BrokerClusterInfo])
              .filter(_.zookeeperClusterName == brokerClusterInfo.zookeeperClusterName)
            if (sameZkNameClusters.nonEmpty)
              throw new IllegalArgumentException(
                s"zk cluster:${brokerClusterInfo.zookeeperClusterName} is already used by broker cluster:${sameZkNameClusters.head.name}")
            clusterCollie.brokerCollie.creator
              .clusterName(brokerClusterInfo.name)
              .clientPort(brokerClusterInfo.clientPort)
              .exporterPort(brokerClusterInfo.exporterPort)
              .jmxPort(brokerClusterInfo.jmxPort)
              .zookeeperClusterName(brokerClusterInfo.zookeeperClusterName)
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

  private[this] def hookOfGroup: HookOfGroup = _ => GROUP_DEFAULT

  def apply(implicit store: DataStore,
            zookeeperCollie: ZookeeperCollie,
            clusterCollie: ClusterCollie,
            nodeCollie: NodeCollie,
            executionContext: ExecutionContext): server.Route =
    RouteUtils.route(
      root = BROKER_PREFIX_PATH,
      hookOfGroup = hookOfGroup,
      hookOfCreation = hookOfCreation,
      hookOfUpdate = hookOfUpdate,
      hookOfGet = hookOfGet,
      hookOfList = hookOfList,
      hookBeforeDelete = hookBeforeDelete,
      collie = clusterCollie.brokerCollie,
      hookOfStart = hookOfStart,
      hookBeforeStop = hookBeforeStop
    )
}
