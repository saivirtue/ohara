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
import com.island.ohara.agent.{ClusterCollie, NodeCollie}
import com.island.ohara.client.configurator.v0.ZookeeperApi._
import com.island.ohara.common.setting.ObjectKey
import com.island.ohara.common.util.CommonUtils
import com.island.ohara.configurator.route.RouteUtils._
import com.island.ohara.configurator.store.DataStore

import scala.concurrent.{ExecutionContext, Future}

object ZookeeperRoute {

  private[this] def updateState(info: ZookeeperClusterInfo)(
    implicit store: DataStore,
    clusterCollie: ClusterCollie,
    executionContext: ExecutionContext): Future[ZookeeperClusterInfo] =
    store
      .value[ZookeeperClusterInfo](info.key)
      .flatMap(
        cluster =>
          clusterCollie.zookeeperCollie
            .exist(cluster.name)
            .flatMap {
              if (_) {
                clusterCollie.zookeeperCollie.cluster(cluster.name).map(_._1)
              } else {
                // if zookeeper cluster was not created, we initial state and error
                Future.successful(cluster.copy(state = None, error = None))
              }
            }
            .flatMap { finalData =>
              store.addIfPresent[ZookeeperClusterInfo](
                key = info.key,
                updater = (previous: ZookeeperClusterInfo) =>
                  previous.copy(
                    state = finalData.state,
                    nodeNames = finalData.nodeNames,
                    deadNodes = finalData.deadNodes
                )
              )
          }
      )

  private[this] def hookOfGet(implicit store: DataStore,
                              clusterCollie: ClusterCollie,
                              executionContext: ExecutionContext): HookOfGet[ZookeeperClusterInfo] = updateState(_)

  private[this] def hookOfList(implicit store: DataStore,
                               clusterCollie: ClusterCollie,
                               executionContext: ExecutionContext): HookOfList[ZookeeperClusterInfo] =
    Future.traverse(_)(updateState)

  private[this] def hookOfCreation: HookOfCreation[Creation, ZookeeperClusterInfo] = (creation: Creation) =>
    Future.successful(
      ZookeeperClusterInfo(
        name = creation.name,
        imageName = creation.imageName,
        clientPort = creation.clientPort,
        peerPort = creation.peerPort,
        electionPort = creation.electionPort,
        nodeNames = creation.nodeNames,
        deadNodes = Set.empty,
        tags = creation.tags,
        state = None,
        error = None,
        lastModified = CommonUtils.current()
      ))

  private[this] def hookOfUpdate(
    implicit clusterCollie: ClusterCollie,
    executionContext: ExecutionContext): HookOfUpdate[Creation, Update, ZookeeperClusterInfo] =
    (key: ObjectKey, update: Update, previous: Option[ZookeeperClusterInfo]) =>
      clusterCollie.zookeeperCollie.clusters().map { clusters =>
        if (clusters.keys.filter(_.name == key.name()).exists(_.state.nonEmpty))
          throw new RuntimeException(s"You cannot update property on non-stopped zookeeper cluster: $key")
        else
          previous.fold(
            ZookeeperClusterInfo(
              name = key.name,
              imageName = update.imageName.getOrElse(IMAGE_NAME_DEFAULT),
              clientPort = update.clientPort.getOrElse(CommonUtils.availablePort()),
              peerPort = update.peerPort.getOrElse(CommonUtils.availablePort()),
              electionPort = update.electionPort.getOrElse(CommonUtils.availablePort()),
              nodeNames = update.nodeNames.getOrElse(Set.empty),
              deadNodes = Set.empty,
              tags = update.tags.getOrElse(Map.empty),
              state = None,
              error = None,
              lastModified = CommonUtils.current()
            )) { previous =>
            previous.copy(
              imageName = update.imageName.getOrElse(previous.imageName),
              clientPort = update.clientPort.getOrElse(previous.clientPort),
              peerPort = update.peerPort.getOrElse(previous.peerPort),
              electionPort = update.electionPort.getOrElse(previous.electionPort),
              nodeNames = update.nodeNames.getOrElse(previous.nodeNames),
              tags = update.tags.getOrElse(previous.tags),
              lastModified = CommonUtils.current()
            )
          }
    }

  private[this] def hookBeforeDelete(implicit store: DataStore,
                                     clusterCollie: ClusterCollie,
                                     executionContext: ExecutionContext): HookBeforeDelete = (key: ObjectKey) =>
    store.get[ZookeeperClusterInfo](key).flatMap {
      _.fold(Future.unit) { info =>
        updateState(info).flatMap { data =>
          if (data.state.isEmpty) Future.unit
          else Future.failed(new RuntimeException(s"You cannot delete a non-stopped zookeeper :$key"))
        }
      }
  }

  private[this] def hookOfStart(implicit store: DataStore,
                                clusterCollie: ClusterCollie,
                                executionContext: ExecutionContext): HookOfAction =
    (key: ObjectKey, _: String, _: Map[String, String]) =>
      store
        .value[ZookeeperClusterInfo](key)
        .flatMap(
          zkClusterInfo =>
            clusterCollie.zookeeperCollie.creator
              .clusterName(zkClusterInfo.name)
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
      .flatMap(
        zkClusterInfo =>
          clusterCollie.brokerCollie
            .clusters()
            .map(
              _.keys
                .find(_.zookeeperClusterName == zkClusterInfo.name)
                .map(cluster =>
                  throw new IllegalArgumentException(
                    s"you can't remove zookeeper cluster:${zkClusterInfo.name} since it is used by broker cluster:${cluster.name}"))
          ))

  private[this] def hookOfGroup: HookOfGroup = _ => GROUP_DEFAULT

  def apply(implicit store: DataStore,
            clusterCollie: ClusterCollie,
            nodeCollie: NodeCollie,
            executionContext: ExecutionContext): server.Route =
    RouteUtils.route(
      root = ZOOKEEPER_PREFIX_PATH,
      hookOfGroup = hookOfGroup,
      hookOfCreation = hookOfCreation,
      hookOfUpdate = hookOfUpdate,
      hookOfGet = hookOfGet,
      hookOfList = hookOfList,
      hookBeforeDelete = hookBeforeDelete,
      collie = clusterCollie.zookeeperCollie,
      hookOfStart = hookOfStart,
      hookBeforeStop = hookBeforeStop
    )
}
