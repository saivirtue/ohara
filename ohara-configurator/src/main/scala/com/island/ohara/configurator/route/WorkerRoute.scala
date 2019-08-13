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
import com.island.ohara.agent._
import com.island.ohara.client.configurator.v0.BrokerApi.BrokerClusterInfo
import com.island.ohara.client.configurator.v0.WorkerApi._
import com.island.ohara.common.setting.ObjectKey
import com.island.ohara.common.util.CommonUtils
import com.island.ohara.configurator.file.FileStore
import com.island.ohara.configurator.route.RouteUtils._
import com.island.ohara.configurator.store.DataStore

import scala.concurrent.{ExecutionContext, Future}
object WorkerRoute {

  private[this] def updateState(info: WorkerClusterInfo)(
    implicit store: DataStore,
    clusterCollie: ClusterCollie,
    executionContext: ExecutionContext): Future[WorkerClusterInfo] =
    store
      .value[WorkerClusterInfo](info.key)
      .flatMap(
        cluster =>
          clusterCollie.workerCollie
            .exist(cluster.name)
            .flatMap {
              if (_) {
                clusterCollie.workerCollie.cluster(cluster.name).map(_._1)
              } else {
                // if cluster was not created, we initial state and error
                Future.successful(cluster.copy(state = None, error = None))
              }
            }
            .flatMap { finalData =>
              store.addIfPresent[WorkerClusterInfo](
                key = info.key,
                updater = (previous: WorkerClusterInfo) =>
                  previous.copy(
                    brokerClusterName = finalData.brokerClusterName,
                    state = finalData.state,
                    nodeNames = finalData.nodeNames,
                    deadNodes = finalData.deadNodes,
                    // the connector list is get by REST, we should update it again
                    connectors = finalData.connectors
                )
              )
          }
      )

  private[this] def hookOfGet(implicit store: DataStore,
                              clusterCollie: ClusterCollie,
                              executionContext: ExecutionContext): HookOfGet[WorkerClusterInfo] =
    updateState

  private[this] def hookOfList(implicit store: DataStore,
                               clusterCollie: ClusterCollie,
                               executionContext: ExecutionContext): HookOfList[WorkerClusterInfo] =
    Future.traverse(_)(updateState)

  private[this] def hookOfCreation(implicit fileStore: FileStore,
                                   brokerCollie: BrokerCollie,
                                   executionContext: ExecutionContext): HookOfCreation[Creation, WorkerClusterInfo] =
    (creation: Creation) =>
      CollieUtils.orElseClusterName(creation.brokerClusterName).flatMap { bkName =>
        Future
          .traverse(creation.jarKeys)(fileStore.fileInfo)
          .map(_.toSeq)
          .map(jarInfos =>
            WorkerClusterInfo(
              name = creation.name,
              imageName = creation.imageName,
              brokerClusterName = bkName,
              clientPort = creation.clientPort,
              jmxPort = creation.jmxPort,
              groupId = creation.groupId,
              statusTopicName = creation.statusTopicName,
              statusTopicPartitions = creation.statusTopicPartitions,
              statusTopicReplications = creation.statusTopicReplications,
              configTopicName = creation.configTopicName,
              configTopicPartitions = 1,
              configTopicReplications = creation.configTopicReplications,
              offsetTopicName = creation.offsetTopicName,
              offsetTopicPartitions = creation.offsetTopicPartitions,
              offsetTopicReplications = creation.offsetTopicReplications,
              jarInfos = jarInfos,
              connectors = Seq.empty,
              nodeNames = creation.nodeNames,
              deadNodes = Set.empty,
              tags = creation.tags,
              state = None,
              error = None,
              lastModified = CommonUtils.current()
          ))
    }

  private[this] def hookOfUpdate(
    implicit fileStore: FileStore,
    clusterCollie: ClusterCollie,
    brokerCollie: BrokerCollie,
    executionContext: ExecutionContext): HookOfUpdate[Creation, Update, WorkerClusterInfo] =
    (key: ObjectKey, update: Update, previousOption: Option[WorkerClusterInfo]) =>
      clusterCollie.workerCollie
        .clusters()
        .flatMap { clusters =>
          if (clusters.keys.filter(_.name == key.name()).exists(_.state.nonEmpty))
            throw new RuntimeException(s"You cannot update property on non-stopped worker cluster: $key")
          CollieUtils.orElseClusterName(update.brokerClusterName.orElse(previousOption.map(_.brokerClusterName)))
        }
        .flatMap { bkName =>
          previousOption.fold(
            // use PUT as creation request
            Future
              .traverse(update.jarKeys.getOrElse(Set.empty))(fileStore.fileInfo)
              .map(_.toSeq)
              .map {
                jarInfos =>
                  val groupId = update.groupId.getOrElse(CommonUtils.randomString(10))
                  WorkerClusterInfo(
                    name = key.name,
                    imageName = update.imageName.getOrElse(IMAGE_NAME_DEFAULT),
                    brokerClusterName = bkName,
                    clientPort = update.clientPort.getOrElse(CommonUtils.availablePort()),
                    jmxPort = update.jmxPort.getOrElse(CommonUtils.availablePort()),
                    groupId = groupId,
                    statusTopicName =
                      update.statusTopicName.getOrElse(s"$groupId-status-${CommonUtils.randomString(10)}"),
                    statusTopicPartitions = update.statusTopicPartitions.getOrElse(1),
                    statusTopicReplications = update.statusTopicReplications.getOrElse(1),
                    configTopicName =
                      update.configTopicName.getOrElse(s"$groupId-config-${CommonUtils.randomString(10)}"),
                    configTopicPartitions = 1,
                    configTopicReplications = update.configTopicReplications.getOrElse(1),
                    offsetTopicName =
                      update.offsetTopicName.getOrElse(s"$groupId-offset-${CommonUtils.randomString(10)}"),
                    offsetTopicPartitions = update.offsetTopicPartitions.getOrElse(1),
                    offsetTopicReplications = update.offsetTopicReplications.getOrElse(1),
                    jarInfos = jarInfos,
                    connectors = Seq.empty,
                    nodeNames = update.nodeNames.getOrElse(Set.empty),
                    deadNodes = Set.empty,
                    tags = update.tags.getOrElse(Map.empty),
                    state = None,
                    error = None,
                    lastModified = CommonUtils.current()
                  )
              }
          ) { previous =>
            Future
              .traverse(update.jarKeys.getOrElse(Set.empty))(fileStore.fileInfo)
              .map(_.toSeq)
              .map(jarInfos =>
                previous.copy(
                  imageName = update.imageName.getOrElse(previous.imageName),
                  brokerClusterName = update.brokerClusterName.getOrElse(previous.brokerClusterName),
                  clientPort = update.clientPort.getOrElse(previous.clientPort),
                  jmxPort = update.jmxPort.getOrElse(previous.jmxPort),
                  groupId = update.groupId.getOrElse(previous.groupId),
                  configTopicName = update.configTopicName.getOrElse(previous.configTopicName),
                  configTopicReplications = update.configTopicReplications.getOrElse(previous.configTopicReplications),
                  offsetTopicName = update.offsetTopicName.getOrElse(previous.offsetTopicName),
                  offsetTopicPartitions = update.offsetTopicPartitions.getOrElse(previous.offsetTopicPartitions),
                  offsetTopicReplications = update.offsetTopicReplications.getOrElse(previous.offsetTopicReplications),
                  statusTopicName = update.statusTopicName.getOrElse(previous.statusTopicName),
                  statusTopicPartitions = update.statusTopicPartitions.getOrElse(previous.statusTopicPartitions),
                  statusTopicReplications = update.statusTopicReplications.getOrElse(previous.statusTopicReplications),
                  jarInfos = if (jarInfos.isEmpty) previous.jarInfos else jarInfos,
                  nodeNames = update.nodeNames.getOrElse(previous.nodeNames),
                  tags = update.tags.getOrElse(previous.tags),
                  lastModified = CommonUtils.current()
              ))
          }
      }

  private[this] def hookBeforeDelete(implicit store: DataStore,
                                     clusterCollie: ClusterCollie,
                                     executionContext: ExecutionContext): HookBeforeDelete =
    (key: ObjectKey) =>
      store.get[WorkerClusterInfo](key).flatMap {
        _.fold(Future.unit)(info =>
          updateState(info).flatMap { data =>
            if (data.state.isEmpty) Future.unit
            else Future.failed(new RuntimeException(s"You cannot delete a non-stopped worker :$key"))
        })
    }

  private[this] def hookOfStart(implicit store: DataStore,
                                clusterCollie: ClusterCollie,
                                executionContext: ExecutionContext): HookOfAction =
    (key: ObjectKey, _: String, _: Map[String, String]) =>
      store
        .value[WorkerClusterInfo](key)
        .flatMap { data =>
          clusterCollie.clusters().map(_.keys.toSeq).map(_ -> data)
        }
        .flatMap {
          case (clusters, workerClusterInfo) =>
            val wkClusters = clusters.filter(_.isInstanceOf[WorkerClusterInfo]).map(_.asInstanceOf[WorkerClusterInfo])

            // check broker cluster
            if (!clusters
                  .filter(_.isInstanceOf[BrokerClusterInfo])
                  .exists(_.name == workerClusterInfo.brokerClusterName))
              throw new NoSuchClusterException(s"broker cluster:${workerClusterInfo.brokerClusterName} doesn't exist")

            // check group id
            wkClusters.find(_.groupId == workerClusterInfo.groupId).foreach { cluster =>
              throw new IllegalArgumentException(
                s"group id:${workerClusterInfo.groupId} is used by wk cluster:${cluster.name}")
            }

            // check setting topic
            wkClusters.find(_.configTopicName == workerClusterInfo.configTopicName).foreach { cluster =>
              throw new IllegalArgumentException(
                s"configTopicName:${workerClusterInfo.configTopicName} is used by wk cluster:${cluster.name}")
            }

            // check offset topic
            wkClusters.find(_.offsetTopicName == workerClusterInfo.offsetTopicName).foreach { cluster =>
              throw new IllegalArgumentException(
                s"offsetTopicName:${workerClusterInfo.offsetTopicName} is used by wk cluster:${cluster.name}")
            }

            // check status topic
            wkClusters.find(_.statusTopicName == workerClusterInfo.statusTopicName).foreach { cluster =>
              throw new IllegalArgumentException(
                s"statusTopicName:${workerClusterInfo.statusTopicName} is used by wk cluster:${cluster.name}")
            }

            clusterCollie.workerCollie.creator
              .clusterName(workerClusterInfo.name)
              .clientPort(workerClusterInfo.clientPort)
              .jmxPort(workerClusterInfo.jmxPort)
              .brokerClusterName(workerClusterInfo.brokerClusterName)
              .groupId(workerClusterInfo.groupId)
              .configTopicName(workerClusterInfo.configTopicName)
              .configTopicReplications(workerClusterInfo.configTopicReplications)
              .offsetTopicName(workerClusterInfo.offsetTopicName)
              .offsetTopicPartitions(workerClusterInfo.offsetTopicPartitions)
              .offsetTopicReplications(workerClusterInfo.offsetTopicReplications)
              .statusTopicName(workerClusterInfo.statusTopicName)
              .statusTopicPartitions(workerClusterInfo.statusTopicPartitions)
              .statusTopicReplications(workerClusterInfo.statusTopicReplications)
              .imageName(workerClusterInfo.imageName)
              .jarInfos(workerClusterInfo.jarInfos)
              .nodeNames(workerClusterInfo.nodeNames)
              .threadPool(executionContext)
              .create()
        }
        .map(_ => Unit)

  private[this] def hookBeforeStop: HookOfAction = (_, _, _) => Future.unit

  private[this] def hookOfGroup: HookOfGroup = _ => GROUP_DEFAULT

  def apply(implicit store: DataStore,
            brokerCollie: BrokerCollie,
            clusterCollie: ClusterCollie,
            nodeCollie: NodeCollie,
            fileStore: FileStore,
            executionContext: ExecutionContext): server.Route =
    RouteUtils.route(
      root = WORKER_PREFIX_PATH,
      hookOfGroup = hookOfGroup,
      hookOfCreation = hookOfCreation,
      hookOfUpdate = hookOfUpdate,
      hookOfGet = hookOfGet,
      hookOfList = hookOfList,
      hookBeforeDelete = hookBeforeDelete,
      collie = clusterCollie.workerCollie,
      hookOfStart = hookOfStart,
      hookBeforeStop = hookBeforeStop
    )
}
