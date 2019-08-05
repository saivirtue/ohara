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

package com.island.ohara.it.agent

import com.island.ohara.client.configurator.v0._
import com.island.ohara.common.util.Releasable
import com.island.ohara.configurator.Configurator
import org.junit.After

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * this test is similar to Test*Collie. However, all operations about collie are executed by configurator.
  * It means all ops are sent to configurator and then configurator will execute them.
  */
abstract class BasicTests4ClusterCollieByConfigurator extends BasicTests4Collie {
  protected def configurator: Configurator

  private[this] def zkApi = ZookeeperApi.access.hostname(configurator.hostname).port(configurator.port)

  private[this] def bkApi = BrokerApi.access.hostname(configurator.hostname).port(configurator.port)

  private[this] def wkApi = WorkerApi.access.hostname(configurator.hostname).port(configurator.port)

  private[this] def logApi = LogApi.access.hostname(configurator.hostname).port(configurator.port)

  private[this] def containerApi = ContainerApi.access.hostname(configurator.hostname).port(configurator.port)

  //--------------------------------------------------[zk operations]--------------------------------------------------//
  override protected def zk_exist(clusterName: String): Future[Boolean] =
    zkApi.list().map(_.exists(_.name == clusterName))

  override protected def zk_create(clusterName: String,
                                   clientPort: Int,
                                   electionPort: Int,
                                   peerPort: Int,
                                   nodeNames: Set[String]): Future[ZookeeperApi.ZookeeperClusterInfo] =
    zkApi.request
      .name(clusterName)
      .clientPort(clientPort)
      .electionPort(electionPort)
      .peerPort(peerPort)
      .nodeNames(nodeNames)
      .create()

  override protected def zk_start(clusterName: String): Future[Unit] = zkApi.start(clusterName)

  override protected def zk_stop(clusterName: String): Future[Unit] = zkApi.forceStop(clusterName).map(_ => Unit)

  override protected def zk_clusters(): Future[Seq[ZookeeperApi.ZookeeperClusterInfo]] = zkApi.list()

  override protected def zk_logs(clusterName: String): Future[Seq[String]] =
    logApi.log4ZookeeperCluster(clusterName).map(_.logs.map(_.value))

  override protected def zk_containers(clusterName: String): Future[Seq[ContainerApi.ContainerInfo]] =
    containerApi.get(clusterName).map(_.flatMap(_.containers))

  override protected def zk_delete(clusterName: String): Future[Unit] = zkApi.delete(clusterName)

  //--------------------------------------------------[bk operations]--------------------------------------------------//
  override protected def bk_exist(clusterName: String): Future[Boolean] =
    bkApi.list().map(_.exists(_.name == clusterName))

  override protected def bk_create(clusterName: String,
                                   clientPort: Int,
                                   exporterPort: Int,
                                   jmxPort: Int,
                                   zkClusterName: String,
                                   nodeNames: Set[String]): Future[BrokerApi.BrokerClusterInfo] = bkApi.request
    .name(clusterName)
    .clientPort(clientPort)
    .exporterPort(exporterPort)
    .jmxPort(jmxPort)
    .zookeeperClusterName(zkClusterName)
    .nodeNames(nodeNames)
    .create()

  override protected def bk_start(clusterName: String): Future[Unit] = bkApi.start(clusterName)

  override protected def bk_stop(clusterName: String): Future[Unit] = bkApi.forceStop(clusterName).map(_ => Unit)

  override protected def bk_clusters(): Future[Seq[BrokerApi.BrokerClusterInfo]] = bkApi.list()

  override protected def bk_logs(clusterName: String): Future[Seq[String]] =
    logApi.log4BrokerCluster(clusterName).map(_.logs.map(_.value))

  override protected def bk_containers(clusterName: String): Future[Seq[ContainerApi.ContainerInfo]] =
    containerApi.get(clusterName).map(_.flatMap(_.containers))

  override protected def bk_delete(clusterName: String): Future[Unit] = bkApi.delete(clusterName)

  override protected def bk_addNode(clusterName: String, nodeName: String): Future[BrokerApi.BrokerClusterInfo] =
    bkApi.addNode(clusterName, nodeName).flatMap(_ => bkApi.get(clusterName))

  override protected def bk_removeNode(clusterName: String, nodeName: String): Future[Unit] =
    bkApi.removeNode(clusterName, nodeName)

  //--------------------------------------------------[wk operations]--------------------------------------------------//
  override protected def wk_exist(clusterName: String): Future[Boolean] =
    wkApi.list().map(_.exists(_.name == clusterName))

  override protected def wk_create(clusterName: String,
                                   clientPort: Int,
                                   jmxPort: Int,
                                   bkClusterName: String,
                                   nodeNames: Set[String]): Future[WorkerApi.WorkerClusterInfo] =
    wkApi.request
      .name(clusterName)
      .clientPort(clientPort)
      .jmxPort(jmxPort)
      .brokerClusterName(bkClusterName)
      .nodeNames(nodeNames)
      .create()

  override protected def wk_create(clusterName: String,
                                   clientPort: Int,
                                   jmxPort: Int,
                                   groupId: String,
                                   configTopicName: String,
                                   statusTopicName: String,
                                   offsetTopicName: String,
                                   bkClusterName: String,
                                   nodeNames: Set[String]): Future[WorkerApi.WorkerClusterInfo] =
    wkApi.request
      .name(clusterName)
      .clientPort(clientPort)
      .jmxPort(jmxPort)
      .brokerClusterName(bkClusterName)
      .nodeNames(nodeNames)
      .groupId(groupId)
      .configTopicName(configTopicName)
      .statusTopicName(statusTopicName)
      .offsetTopicName(offsetTopicName)
      .create()

  override protected def wk_start(clusterName: String): Future[Unit] = wkApi.start(clusterName)

  override protected def wk_stop(clusterName: String): Future[Unit] = wkApi.forceStop(clusterName).map(_ => Unit)

  override protected def wk_clusters(): Future[Seq[WorkerApi.WorkerClusterInfo]] = wkApi.list()

  override protected def wk_logs(clusterName: String): Future[Seq[String]] =
    logApi.log4WorkerCluster(clusterName).map(_.logs.map(_.value))

  override protected def wk_containers(clusterName: String): Future[Seq[ContainerApi.ContainerInfo]] =
    containerApi.get(clusterName).map(_.flatMap(_.containers))

  override protected def wk_delete(clusterName: String): Future[Unit] = wkApi.delete(clusterName)

  override protected def wk_addNode(clusterName: String, nodeName: String): Future[WorkerApi.WorkerClusterInfo] =
    wkApi.addNode(clusterName, nodeName).flatMap(_ => wkApi.get(clusterName))

  override protected def wk_removeNode(clusterName: String, nodeName: String): Future[Unit] =
    wkApi.removeNode(clusterName, nodeName)

  @After
  final def tearDown(): Unit = Releasable.close(configurator)
}
