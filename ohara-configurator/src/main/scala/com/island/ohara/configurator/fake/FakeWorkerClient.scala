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

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

import com.island.ohara.client.configurator.v0.ConnectorApi.ConnectorState
import com.island.ohara.client.kafka.WorkerClient
import com.island.ohara.client.kafka.WorkerClient.Validator
import com.island.ohara.client.kafka.WorkerJson.{
  ConnectorConfig,
  ConnectorCreationResponse,
  ConnectorInfo,
  ConnectorStatus,
  Plugin,
  TaskStatus
}
import com.island.ohara.common.setting.{ConnectorKey, Definition}
import com.island.ohara.kafka.connector.json._
import com.island.ohara.kafka.connector.{RowSinkConnector, RowSourceConnector}
import com.typesafe.scalalogging.Logger
import org.apache.kafka.connect.runtime.AbstractHerder
import org.apache.kafka.connect.sink.SinkConnector
import org.apache.kafka.connect.source.SourceConnector
import org.reflections.Reflections
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

private[configurator] class FakeWorkerClient extends WorkerClient {
  private[this] val cachedConnectors = new ConcurrentHashMap[String, Map[String, String]]()
  private[this] val cachedConnectorsState = new ConcurrentHashMap[String, ConnectorState]()

  override def connectorCreator(): WorkerClient.Creator = (_, creation) => {
    if (cachedConnectors.contains(creation.name()))
      Future.failed(new IllegalStateException(s"the connector:${creation.name()} exists!"))
    else {
      import scala.collection.JavaConverters._
      cachedConnectors.put(creation.name(), creation.configs().asScala.toMap)
      cachedConnectorsState.put(creation.name(), ConnectorState.RUNNING)
      Future.successful(ConnectorCreationResponse(creation.name(), creation.configs().asScala.toMap, Seq.empty))
    }
  }

  override def delete(connectorKey: ConnectorKey)(implicit executionContext: ExecutionContext): Future[Unit] =
    try if (cachedConnectors.remove(connectorKey.connectorNameOnKafka()) == null)
      Future.failed(new IllegalStateException(s"Connector:${connectorKey.connectorNameOnKafka()} doesn't exist!"))
    else Future.successful(())
    finally cachedConnectorsState.remove(connectorKey.connectorNameOnKafka())
  // TODO; does this work? by chia
  override def plugins()(implicit executionContext: ExecutionContext): Future[Seq[Plugin]] =
    Future.successful(cachedConnectors.keys.asScala.map(Plugin(_, "unknown", "unknown")).toSeq)
  override def activeConnectors()(implicit executionContext: ExecutionContext): Future[Seq[String]] =
    Future.successful(cachedConnectors.keys.asScala.toSeq)
  override def connectionProps: String = "Unknown"
  override def status(connectorKey: ConnectorKey)(implicit executionContext: ExecutionContext): Future[ConnectorInfo] =
    if (!cachedConnectors.containsKey(connectorKey.connectorNameOnKafka()))
      Future.failed(new IllegalArgumentException(s"Connector:${connectorKey.connectorNameOnKafka()} doesn't exist"))
    else
      Future.successful(
        ConnectorInfo(connectorKey.connectorNameOnKafka(),
                      ConnectorStatus(cachedConnectorsState.get(connectorKey.connectorNameOnKafka()), "fake id", None),
                      Seq.empty))

  override def config(connectorKey: ConnectorKey)(
    implicit executionContext: ExecutionContext): Future[ConnectorConfig] = {
    val map = cachedConnectors.get(connectorKey.connectorNameOnKafka())
    if (map == null)
      Future.failed(new IllegalArgumentException(s"${connectorKey.connectorNameOnKafka()} doesn't exist"))
    else Future.successful(map.toJson.convertTo[ConnectorConfig])
  }

  override def taskStatus(connectorKey: ConnectorKey, id: Int)(
    implicit executionContext: ExecutionContext): Future[TaskStatus] =
    if (!cachedConnectors.containsKey(connectorKey.connectorNameOnKafka()))
      Future.failed(new IllegalArgumentException(s"${connectorKey.connectorNameOnKafka()} doesn't exist"))
    else
      Future.successful(
        TaskStatus(0, cachedConnectorsState.get(connectorKey.connectorNameOnKafka()), "worker_id", None))

  override def pause(connectorKey: ConnectorKey)(implicit executionContext: ExecutionContext): Future[Unit] =
    if (!cachedConnectors.containsKey(connectorKey.connectorNameOnKafka()))
      Future.failed(new IllegalArgumentException(s"${connectorKey.connectorNameOnKafka()} doesn't exist"))
    else Future.successful(cachedConnectorsState.put(connectorKey.connectorNameOnKafka(), ConnectorState.PAUSED))

  override def resume(connectorKey: ConnectorKey)(implicit executionContext: ExecutionContext): Future[Unit] =
    if (!cachedConnectors.containsKey(connectorKey.connectorNameOnKafka()))
      Future.failed(new IllegalArgumentException(s"${connectorKey.connectorNameOnKafka()} doesn't exist"))
    else Future.successful(cachedConnectorsState.put(connectorKey.connectorNameOnKafka(), ConnectorState.RUNNING))

  override def connectorValidator(): Validator =
    // TODO: this implementation use kafka private APIs ... by chia
    (executionContext, validation) =>
      Future {
        val instance = Class.forName(validation.className).newInstance()
        val (connectorType, configDef, values) = instance match {
          case c: SourceConnector => ("source", c.config(), c.config().validate(validation.settings))
          case c: SinkConnector   => ("sink", c.config(), c.config().validate(validation.settings))
          case _                  => throw new IllegalArgumentException(s"who are you ${validation.className} ???")
        }
        SettingInfo.of(
          AbstractHerder.generateResult(connectorType, configDef.configKeys(), values, Collections.emptyList()))
      }(executionContext)

  override def connectorDefinitions()(implicit executionContext: ExecutionContext): Future[Seq[Definition]] =
    Future.successful(FakeWorkerClient.localConnectorDefinitions)
}

object FakeWorkerClient {
  private[this] val LOG = Logger(FakeWorkerClient.getClass)
  def apply(): FakeWorkerClient = new FakeWorkerClient

  /**
    * Dynamically instantiate local connector classes and then fetch the definitions from them.
    * @return local connector definitions
    */
  private[configurator] lazy val localConnectorDefinitions: Seq[Definition] = {
    val reflections = new Reflections()
    val classes = reflections.getSubTypesOf(classOf[RowSourceConnector]).asScala ++ reflections
      .getSubTypesOf(classOf[RowSinkConnector])
      .asScala
    classes
      .flatMap { clz =>
        try Some((clz.getName, clz.newInstance().definitions().asScala))
        catch {
          case e: Throwable =>
            LOG.error(s"failed to instantiate ${clz.getName} for RowSourceConnector", e)
            None
        }
      }
      .map { entry =>
        Definition.of(entry._1, entry._2.asJava)
      }
      .toSeq
  }
}
