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

import com.island.ohara.agent.docker.ContainerState
import com.island.ohara.client.configurator.v0.BrokerApi.BrokerClusterInfo
import com.island.ohara.client.configurator.v0.ContainerApi.ContainerInfo
import com.island.ohara.client.configurator.v0.WorkerApi.WorkerClusterInfo
import com.island.ohara.client.configurator.v0.{BrokerApi, ClusterInfo, NodeApi, TopicApi}
import com.island.ohara.common.util.CommonUtils

import scala.concurrent.{ExecutionContext, Future}

class FakeWorkerCollie(node: NodeCollie) extends WorkerCollie {
  override protected def brokerClusters(
    implicit executionContext: ExecutionContext): Future[Map[ClusterInfo, Seq[ContainerInfo]]] = Future.successful(
    Map(
      BrokerClusterInfo(
        name = "bk1",
        imageName = BrokerApi.IMAGE_NAME_DEFAULT,
        zookeeperClusterName = "zk1",
        clientPort = 2181,
        exporterPort = 2182,
        jmxPort = 2183,
        nodeNames = Set("node1"),
        deadNodes = Set.empty,
        tags = Map.empty,
        lastModified = CommonUtils.current(),
        state = None,
        error = None,
        topicSettingDefinitions = TopicApi.TOPIC_DEFINITIONS
      ) -> Seq(ContainerInfo(
        "node1",
        "aaaa",
        "broker",
        "2019-05-28 00:00:00",
        "running",
        "unknown",
        "ohara-xxx-bk-0000",
        "unknown",
        Seq.empty,
        Map(BrokerCollie.CLIENT_PORT_KEY -> "9092"),
        "ohara-xxx-bk-0000"
      )))
  )

  override protected def doCreator(executionContext: ExecutionContext,
                                   clusterName: String,
                                   containerName: String,
                                   containerInfo: ContainerInfo,
                                   node: NodeApi.Node,
                                   route: Map[String, String]): Future[Unit] = {
    //Nothing
    Future.unit
  }

  override def remove(clusterName: String)(implicit executionContext: ExecutionContext): Future[Boolean] =
    throw new UnsupportedOperationException("FakeWorkerCollie doesn't support remove function")

  override def logs(clusterName: String)(
    implicit executionContext: ExecutionContext): Future[Map[ContainerInfo, String]] =
    throw new UnsupportedOperationException("FakeWorkerCollie doesn't support logs function")

  override def clusterWithAllContainers()(
    implicit executionContext: ExecutionContext): Future[Map[WorkerClusterInfo, Seq[ContainerInfo]]] =
    Future.successful(
      Map(
        WorkerClusterInfo(
          "wk1",
          "worker",
          "bk1",
          8083,
          8084,
          "aaa",
          "statustopic",
          1,
          1,
          "conftopic",
          1,
          1,
          "offsettopic",
          1,
          1,
          Seq.empty,
          Seq.empty,
          Set("node1"),
          Set.empty,
          Map.empty,
          0L,
          Some(ContainerState.RUNNING.name),
          None
        ) -> Seq(
          ContainerInfo("node1",
                        "aaaa",
                        "connect-worker",
                        "2019-05-28 00:00:00",
                        "RUNNING",
                        "unknown",
                        "ohara-xxx-wk-0000",
                        "unknown",
                        Seq.empty,
                        Map.empty,
                        "ohara-xxx-wk-0000")))
    )

  override def addNode(clusterName: String, nodeName: String)(
    implicit executionContext: ExecutionContext): Future[WorkerClusterInfo] =
    throw new UnsupportedOperationException("FakeWorkCollie doesn't support addNode function")

  override def removeNode(clusterName: String, nodeName: String)(
    implicit executionContext: ExecutionContext): Future[Boolean] =
    throw new UnsupportedOperationException("FakeWorkCollie doesn't support removeNode function")

  override protected def resolveHostName(node: String): String = "1.1.1.1"

  /**
    * Please implement nodeCollie
    *
    * @return
    */
  override protected def nodeCollie: NodeCollie = node

  /**
    * Implement prefix name for paltform
    *
    * @return
    */
  override protected def prefixKey: String = "fakeworker"

  /**
    * return service name
    *
    * @return
    */
  override protected def serviceName: String = "wk"
}
