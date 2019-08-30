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
import com.island.ohara.client.configurator.v0.DefinitionApi.Params
import com.island.ohara.client.configurator.v0.MetricsApi.Metrics
import com.island.ohara.client.configurator.v0.StreamApi._
import com.island.ohara.client.kafka.TopicAdmin.TopicInfo
import com.island.ohara.common.setting.{ObjectKey, TopicKey}
import com.island.ohara.common.util.CommonUtils
import com.island.ohara.configurator.file.FileStore
import com.island.ohara.configurator.route.hook.{HookOfAction, HookOfCreation, HookOfGroup, HookOfUpdate}
import com.island.ohara.configurator.store.{DataStore, MeterCache}
import com.island.ohara.streams.config.StreamDefUtils
import spray.json._

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random
private[configurator] object StreamRoute {

  /**
    * The group for a stream application metrics
    * Since each streamApp has it's own metrics, it is OK to use same value
    */
  private[configurator] val STREAM_APP_GROUP = StreamDefUtils.STREAMAPP_METRIC_GROUP_DEFINITION.defaultValue()

  private[this] def assertParameters(streamClusterInfo: StreamClusterInfo): StreamClusterInfo = {
    CommonUtils.requireNonEmpty(streamClusterInfo.name, () => "name fail assert")
    CommonUtils.requireConnectionPort(streamClusterInfo.jmxPort)
    // we don't check the jar key for creation/update since it breaks the APIs.
    // https://github.com/oharastream/ohara/issues/2151
    // Objects.requireNonNull(streamClusterInfo.jarKey)
    streamClusterInfo
  }

  /**
    * Assert the require streamApp properties before running
    *
    * @param streamClusterInfo streamApp data
    */
  private[this] def assertParameters(streamClusterInfo: StreamClusterInfo,
                                     topicInfos: Seq[TopicInfo]): StreamClusterInfo = {
    def checkStoppedTopics(topicKeys: Set[TopicKey], prefix: String): Unit = {
      CommonUtils.requireNonEmpty(topicKeys.asJava, () => s"$prefix topics can't be empty")
      // check the from/to topic size equals one
      // TODO: this is a workaround to avoid input multiple topics
      // TODO: please refactor this after the single from/to topic issue resolved...by Sam
      if (topicKeys.size > 1)
        throw new IllegalArgumentException(
          s"We don't allow multiple topics of $prefix field, actual: ${topicKeys.mkString(",")}")
      val stoppedFromTopics = topicKeys.filterNot(topicKey => topicInfos.exists(_.name == topicKey.topicNameOnKafka()))
      if (stoppedFromTopics.nonEmpty)
        throw new NoSuchElementException(s"topics:${stoppedFromTopics.mkString(",")} is not running")
    }
    checkStoppedTopics(streamClusterInfo.from, "from")
    checkStoppedTopics(streamClusterInfo.to, "to")
    assertParameters(streamClusterInfo)
  }

  /**
    * fina the broker cluster for this creation. The rules are shown below.
    * 1) fp;nd the defined value for broker cluster name
    * 2) find the group of jar key to seek the worker cluster and its broker cluster
    * (TODO: this is a really really really ugly design and it will be fixed by https://github.com/oharastream/ohara/issues/2151)
    * 2.1) find the single broker cluster if the group of jar key is not associated to any worker cluster
    * 3) find the single broker cluster
    * 4) throw exception
    */
  private[this] def pickBrokerCluster(brokerClusterName: Option[String], jarKey: Option[ObjectKey])(
    implicit workerCollie: WorkerCollie,
    brokerCollie: BrokerCollie,
    executionContext: ExecutionContext): Future[String] = if (brokerClusterName.isDefined)
    Future.successful(brokerClusterName.get)
  else if (jarKey.isDefined) workerCollie.clusters().map(_.keys).flatMap { workerClusters =>
    workerClusters
      .find(_.name == jarKey.get.group())
      .map(_.brokerClusterName)
      .map(Future.successful)
      .getOrElse(CollieUtils.singleCluster[BrokerClusterInfo]())
  } else CollieUtils.singleCluster[BrokerClusterInfo]()

  /**
    * This is a temporary solution for using both nodeNames and instances
    * Decide streamApp running nodes. Rules:
    * 1) If both instances and nodeNames were not defined, return empty
    * 2) If instances was defined and nodeNames was not empty, throw exception
    * 3) If instances was not defined but nodeNames is defined
    * 3.1) If some nodes were not defined in nodeCollie, throw exception
    * 3.2) return nodeNames
    * 4) If instances is bigger than nodeCollie size, throw exception
    * 5) Random pick node name from nodeCollie of instances size
    *
    * @param nodeNamesOption node name list
    * @param instancesOption running instances
    * @param executionContext execution context
    * @param nodeCollie node collie
    * @return actual node name list
    */
  private[this] def pickNodeNames(nodeNamesOption: Option[Set[String]], instancesOption: Option[Int])(
    implicit executionContext: ExecutionContext,
    nodeCollie: NodeCollie): Future[Option[Set[String]]] =
    nodeCollie.nodes().map(n => n.map(_.name)).map { all =>
      instancesOption.fold(
        // not define instances, use nodeNames instead
        // If there were some nodes that nodeCollie doesn't contain, throw exception
        nodeNamesOption.fold[Option[Set[String]]](
          // both instances and nodeNames are not defined, return None
          None
        ) { nodeNames =>
          if (nodeNames.forall(all.contains))
            // you are fine to going use it
            Some(nodeNames)
          else
            // we find a node that is not belong to nodeCollie , throw error
            throw new IllegalArgumentException(
              s"Some nodes could not be found, expected: $nodeNames, actual: $all"
            )
        }
      ) { instances =>
        if (nodeNamesOption.isDefined && nodeNamesOption.get.nonEmpty)
          throw new IllegalArgumentException(
            s"You cannot define both nodeNames[$nodeNamesOption] and instances[$instances]")
        if (all.size < instances)
          throw new IllegalArgumentException(
            s"You cannot set instances bigger than actual node list. Expect instances: $instances, actual: ${all.size}")
        Some(Random.shuffle(all).take(instances).toSet)
      }
    }

  private[this] def hookOfCreation(implicit nodeCollie: NodeCollie,
                                   workerCollie: WorkerCollie,
                                   brokerCollie: BrokerCollie,
                                   executionContext: ExecutionContext): HookOfCreation[Creation, StreamClusterInfo] =
    (creation: Creation) =>
      pickBrokerCluster(creation.brokerClusterName, creation.jarKey).flatMap { bkName =>
        //TODO remove this after #2288
        pickNodeNames(Some(creation.nodeNames), creation.instances)
          .map(nodes =>
            StreamClusterInfo(
              settings = creation.settings + (StreamDefUtils.BROKER_CLUSTER_NAME_DEFINITION.key() -> JsString(bkName)),
              nodeNames = nodes.getOrElse(Set.empty),
              deadNodes = Set.empty,
              state = None,
              metrics = Metrics(Seq.empty),
              error = None,
              lastModified = CommonUtils.current()
          ))
          .map(assertParameters)
    }

  private[this] def hookOfUpdate(
    implicit nodeCollie: NodeCollie,
    workerCollie: WorkerCollie,
    brokerCollie: BrokerCollie,
    streamCollie: StreamCollie,
    executionContext: ExecutionContext): HookOfUpdate[Creation, Update, StreamClusterInfo] =
    (key: ObjectKey, update: Update, previousOption: Option[StreamClusterInfo]) =>
      streamCollie.clusters
        .flatMap { clusters =>
          if (clusters.keys.filter(_.name == key.name()).exists(_.state.nonEmpty))
            throw new RuntimeException(s"You cannot update property on non-stopped StreamApp cluster: $key")
          pickBrokerCluster(update.brokerClusterName.orElse(previousOption.map(_.brokerClusterName)),
                            update.jarKey.orElse(previousOption.map(_.jarKey))).flatMap(
            bkName =>
              //TODO remove this after #2288
              pickNodeNames(update.nodeNames, update.instances).map { nodes =>
                var extra_settings =
                  Map[String, JsValue](StreamDefUtils.BROKER_CLUSTER_NAME_DEFINITION.key() -> JsString(bkName))
                if (nodes.isDefined)
                  extra_settings += StreamDefUtils.NODE_NAMES_DEFINITION.key() -> JsArray(
                    nodes.get.map(JsString(_)).toVector)
                update.copy(
                  settings = update.settings ++ extra_settings
                )
            }
          )
        }
        .map { update =>
          StreamClusterInfo(
            settings = previousOption.map(_.settings).getOrElse(Map.empty) ++ update.settings,
            nodeNames = update.nodeNames.orElse(previousOption.map(_.nodeNames)).getOrElse(Set.empty),
            // this cluster is not running so we don't need to keep the dead nodes in the updated cluster.
            deadNodes = Set.empty,
            state = None,
            metrics = Metrics.EMPTY,
            error = None,
            lastModified = CommonUtils.current()
          )
        }
        .map(assertParameters)

  private[this] def hookOfStart(implicit store: DataStore,
                                fileStore: FileStore,
                                nodeCollie: NodeCollie,
                                streamCollie: StreamCollie,
                                brokerCollie: BrokerCollie,
                                executionContext: ExecutionContext): HookOfAction =
    (key: ObjectKey, _, _) =>
      store
        .value[StreamClusterInfo](key)
        .flatMap { info =>
          // check the values by definition
          //TODO move this to RouteUtils in #2191
          streamCollie.fetchDefinitions(Params(info.imageName, Some(info.jarInfo))).map { definition =>
            var copy = info.settings
            definition.definitions
              .stream()
              .forEach(settingDef =>
                // add the (key, defaultValue) to settings if absent
                if (!copy.contains(settingDef.key()) && !CommonUtils.isEmpty(settingDef.defaultValue()))
                  copy += settingDef.key() -> JsString(settingDef.defaultValue()))
            info.plain.foreach {
              case (k, v) =>
                definition.definitions
                  .stream()
                  .filter(_.key() == k)
                  .findFirst()
                  .orElseThrow(() => new IllegalArgumentException(s"$k not found in definition"))
                  .checker()
                  .accept(v)
            }
            info.copy(settings = copy)
          }
        }
        .flatMap { streamClusterInfo =>
          brokerCollie
            .topicAdmin(streamClusterInfo.brokerClusterName)
            .flatMap {
              case (brokerClusterInfo, topicAdmin) =>
                topicAdmin.topics().map { topicInfos =>
                  try brokerClusterInfo -> topicInfos
                  finally topicAdmin.close()
                }
            }
            .map {
              case (brokerClusterInfo, topicInfos) =>
                assertParameters(streamClusterInfo, topicInfos)
                brokerClusterInfo
            }
            .flatMap { brokerClusterInfo =>
              fileStore.fileInfo(streamClusterInfo.jarKey).flatMap { fileInfo =>
                streamCollie.creator
                  .clusterName(streamClusterInfo.name)
                  .imageName(IMAGE_NAME_DEFAULT)
                  .jarInfo(fileInfo)
                  // these settings will send to container environment
                  // we convert all value to string for convenient
                  .settings(streamClusterInfo.settings)
                  .setting(StreamDefUtils.BROKER_DEFINITION.key(), JsString(brokerClusterInfo.connectionProps))
                  // This nodeNames() should put after settings() because we decide nodeName in starting phase
                  // TODO: the order should not be a problem and please refactor this in #2288
                  .nodeNames(streamClusterInfo.nodeNames)
                  .setting(StreamDefUtils.EXACTLY_ONCE_DEFINITION.key(),
                           JsString(streamClusterInfo.exactlyOnce.toString))
                  .setting(StreamDefUtils.EXACTLY_ONCE_DEFINITION.key(),
                           JsString(streamClusterInfo.exactlyOnce.toString))
                  .brokerClusterName(brokerClusterInfo.name)
                  .threadPool(executionContext)
                  .create()
              }
            }
        }
        .map(_ => Unit)

  private[this] def hookBeforeStop: HookOfAction = (_, _, _) => Future.unit

  private[this] def hookOfGroup: HookOfGroup = _ => GROUP_DEFAULT

  def apply(implicit store: DataStore,
            nodeCollie: NodeCollie,
            streamCollie: StreamCollie,
            clusterCollie: ClusterCollie,
            workerCollie: WorkerCollie,
            brokerCollie: BrokerCollie,
            fileStore: FileStore,
            meterCache: MeterCache,
            executionContext: ExecutionContext): server.Route =
    clusterRoute(
      root = STREAM_PREFIX_PATH,
      metricsKey = Some(STREAM_APP_GROUP),
      hookOfGroup = hookOfGroup,
      hookOfCreation = hookOfCreation,
      hookOfUpdate = hookOfUpdate,
      hookOfStart = hookOfStart,
      hookBeforeStop = hookBeforeStop
    )
}
