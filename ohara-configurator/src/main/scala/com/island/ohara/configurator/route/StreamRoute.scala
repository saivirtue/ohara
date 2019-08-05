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

import java.util.Objects

import akka.http.scaladsl.server
import com.island.ohara.agent.{BrokerCollie, ClusterCollie, NodeCollie, WorkerCollie}
import com.island.ohara.client.configurator.v0.MetricsApi.Metrics
import com.island.ohara.client.configurator.v0.StreamApi._
import com.island.ohara.client.kafka.TopicAdmin.TopicInfo
import com.island.ohara.common.setting.ObjectKey
import com.island.ohara.common.util.CommonUtils
import com.island.ohara.configurator.file.FileStore
import com.island.ohara.configurator.route.RouteUtils._
import com.island.ohara.configurator.store.{DataStore, MeterCache}
import com.island.ohara.streams.config.StreamDefinitions.DefaultConfigs
import org.slf4j.LoggerFactory
import spray.json.{JsNumber, JsObject, _}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

private[configurator] object StreamRoute {

  /**
    * Note: please modified the value in '''MetricFactory''' also if you want to change this
    */
  private[this] val STREAM_APP_GROUP = "streamapp"
  private[this] val log = LoggerFactory.getLogger(StreamRoute.getClass)

  /**
    * Save the streamApp properties.
    * This method will try to fetch the definitions of custom jar.
    * Note: request fields must have definition to used in streamApp.
    *
    * @param req the creation request
    * @return '''StreamApp''' object
    */
  private[this] def toStore(req: Creation)(implicit
                                           fileStore: FileStore,
                                           clusterCollie: ClusterCollie,
                                           executionContext: ExecutionContext): Future[StreamClusterInfo] = {
    req.jarKey.fold {
      log.info(s"there is no jar provided, we skip definition...")
      Future.successful(
        StreamClusterInfo(
          settings = req.settings,
          definition = None,
          nodeNames = req.nodeNames,
          deadNodes = Set.empty,
          state = None,
          metrics = Metrics(Seq.empty),
          error = None,
          lastModified = CommonUtils.current()
        ))
    } { jarKey =>
      fileStore
        .fileInfo(jarKey)
        .map(_.url)
        .flatMap(
          url =>
            clusterCollie.streamCollie
              .definitions(url)
              .map(streamDefOption =>
                StreamClusterInfo(
                  settings = req.settings,
                  definition = streamDefOption,
                  nodeNames = req.nodeNames,
                  deadNodes = Set.empty,
                  state = None,
                  metrics = Metrics(Seq.empty),
                  error = None,
                  lastModified = CommonUtils.current()
              )))
    }
  }

  /**
    * Update the streamApp cluster state.
    *
    * @param info the streamApp data
    * @param store data store
    * @param clusterCollie the management of cluster
    * @param executionContext execution context
    * @return updated streamApp data
    */
  private[this] def updateState(info: StreamClusterInfo)(
    implicit
    store: DataStore,
    clusterCollie: ClusterCollie,
    meterCache: MeterCache,
    executionContext: ExecutionContext): Future[StreamClusterInfo] = {
    store
      .value[StreamClusterInfo](info.key)
      .flatMap(cluster =>
        clusterCollie.streamCollie.exist(cluster.name).flatMap {
          if (_) {
            clusterCollie.streamCollie.cluster(cluster.name).map(_._1)
          } else {
            // if stream cluster was not created, we initial an empty class
            Future.successful(cluster.copy(state = None, error = None))
          }
      })
      .flatMap { finalData =>
        store.addIfPresent[StreamClusterInfo](
          key = finalData.key,
          updater = (previous: StreamClusterInfo) =>
            previous.copy(
              nodeNames = finalData.nodeNames,
              deadNodes = finalData.deadNodes,
              state = finalData.state,
              error = finalData.error,
              metrics = Metrics(meterCache.meters(finalData).getOrElse(STREAM_APP_GROUP, Seq.empty))
          )
        )
      }
  }

  /**
    * Check if field was defined, throw exception otherwise
    *
    * @param key the request object key
    * @param field the testing field
    * @param fieldName field name
    * @tparam T field type
    * @return field
    */
  private[this] def checkField[T](key: ObjectKey, field: Option[T], fieldName: String): T =
    field.getOrElse(throw new IllegalArgumentException(RouteUtils.errorMessage(key, fieldName)))

  /**
    * Assert the require streamApp properties before running
    *
    * @param data streamApp data
    */
  private[this] def assertParameters(data: StreamClusterInfo, topicInfos: Seq[TopicInfo]): Unit = {
    CommonUtils.requireNonEmpty(data.name, () => "name fail assert")
    CommonUtils.requireConnectionPort(data.jmxPort)
    Objects.requireNonNull(data.jarKey)
    // from topic should be defined and starting
    val fromTopics = CommonUtils.requireNonEmpty(data.from.asJava, () => "from topic fail assert")
    if (!topicInfos.exists(t => fromTopics.contains(t.name)))
      throw new NoSuchElementException(s"topic:$fromTopics is not running")

    // to topic should be defined and starting
    val toTopics = CommonUtils.requireNonEmpty(data.to.asJava, () => "to topic fail assert")
    if (!topicInfos.exists(t => toTopics.contains(t.name)))
      throw new NoSuchElementException(s"topic:$toTopics is not running")
  }

  private[this] def hookOfGet(implicit store: DataStore,
                              clusterCollie: ClusterCollie,
                              meterCache: MeterCache,
                              executionContext: ExecutionContext): HookOfGet[StreamClusterInfo] = updateState

  private[this] def hookOfList(implicit store: DataStore,
                               clusterCollie: ClusterCollie,
                               meterCache: MeterCache,
                               executionContext: ExecutionContext): HookOfList[StreamClusterInfo] =
    Future.traverse(_)(updateState)

  private[this] def hookOfCreation(implicit fileStore: FileStore,
                                   clusterCollie: ClusterCollie,
                                   executionContext: ExecutionContext): HookOfCreation[Creation, StreamClusterInfo] =
    (creation: Creation) => toStore(creation)

  private[this] def hookOfUpdate: HookOfUpdate[Creation, Update, StreamClusterInfo] =
    (key: ObjectKey, req: Update, previousOption: Option[StreamClusterInfo]) => {
      val updateReq = previousOption.fold(
        // data not exists, we used PUT as create object method
        StreamClusterInfo(
          settings = req.settings ++
            Map(
              DefaultConfigs.NAME_DEFINITION.key() -> JsString(key.name),
              DefaultConfigs.FROM_TOPICS_DEFINITION.key() -> JsArray(req.from.map(JsString(_)).toVector),
              DefaultConfigs.TO_TOPICS_DEFINITION.key() -> JsArray(req.to.map(JsString(_)).toVector),
              DefaultConfigs.JAR_KEY_DEFINITION.key() -> {
                val jarKey = checkField(key, req.jarKey, DefaultConfigs.JAR_KEY_DEFINITION.key())
                JsString(ObjectKey.toJsonString(jarKey))
              },
              DefaultConfigs.IMAGE_NAME_DEFINITION.key() -> JsString(req.imageName.getOrElse(IMAGE_NAME_DEFAULT)),
              DefaultConfigs.INSTANCES_DEFINITION.key() -> JsNumber(
                req.nodeNames.fold(checkField(key, req.instances, "instances"))(_.size)
              ),
              DefaultConfigs.TAGS_DEFINITION.key() -> JsObject(req.tags.getOrElse(Map.empty))
            ),
          definition = None,
          nodeNames = req.nodeNames.getOrElse(Set.empty),
          deadNodes = Set.empty,
          state = None,
          metrics = Metrics(Seq.empty),
          error = None,
          lastModified = CommonUtils.current()
        )
      ) { previous =>
        previous.copy(
          settings = previous.settings ++
            Map(
              DefaultConfigs.IMAGE_NAME_DEFINITION.key() -> JsString(
                req.imageName.getOrElse(previous.imageName)
              ),
              DefaultConfigs.INSTANCES_DEFINITION.key() -> JsNumber(
                req.instances.getOrElse(previous.instances)
              ),
              DefaultConfigs.FROM_TOPICS_DEFINITION.key() -> {
                if (req.from.isEmpty) JsArray(previous.from.map(JsString(_)).toVector)
                else JsArray(req.from.map(JsString(_)).toVector)
              },
              DefaultConfigs.TO_TOPICS_DEFINITION.key() -> {
                if (req.to.isEmpty) JsArray(previous.to.map(JsString(_)).toVector)
                else JsArray(req.to.map(JsString(_)).toVector)
              },
              DefaultConfigs.JMX_PORT_DEFINITION.key() -> JsNumber(
                req.jmxPort.getOrElse(previous.jmxPort)
              ),
              DefaultConfigs.JAR_KEY_DEFINITION.key() ->
                JsString(ObjectKey.toJsonString(req.jarKey.getOrElse(previous.jarKey))),
              DefaultConfigs.TAGS_DEFINITION.key() -> JsObject(
                req.tags.getOrElse(previous.tags)
              )
            ),
          nodeNames = req.nodeNames.getOrElse(previous.nodeNames)
        )
      }
      if (updateReq.state.isDefined)
        throw new RuntimeException(
          s"You cannot update property on non-stopped streamApp: $key"
        )
      else Future.successful(updateReq)
    }

  private[this] def hookBeforeDelete(implicit store: DataStore,
                                     clusterCollie: ClusterCollie,
                                     meterCache: MeterCache,
                                     executionContext: ExecutionContext): HookBeforeDelete = (key: ObjectKey) =>
    // get the latest status first
    store.get[StreamClusterInfo](key).flatMap {
      _.fold(Future.unit) { desc =>
        updateState(desc).flatMap { data =>
          if (data.state.isEmpty) {
            // state is not exists, could remove this streamApp
            Future.unit
          } else Future.failed(new RuntimeException(s"You cannot delete a non-stopped streamApp :$key"))
        }
      }
  }

  private[this] def hookOfStart(implicit store: DataStore,
                                fileStore: FileStore,
                                adminCleaner: AdminCleaner,
                                nodeCollie: NodeCollie,
                                clusterCollie: ClusterCollie,
                                workerCollie: WorkerCollie,
                                brokerCollie: BrokerCollie,
                                executionContext: ExecutionContext): HookOfAction =
    (key: ObjectKey, _, _) =>
      store.value[StreamClusterInfo](key).flatMap { data =>
        // we assume streamApp has following conditions:
        // 1) use any available node of worker cluster to run streamApp
        // 2) use one from/to pair topic (multiple from/to topics will need to discuss flow)
        // get the broker info and topic info from worker cluster name
        // TODO: decouple this cryptic dependency ... by chia (see https://github.com/oharastream/ohara/issues/2151)
        CollieUtils
          .both(Some(data.jarKey.group()))
          // get broker props from worker cluster
          .flatMap {
            case (_, topicAdmin, _, _) => topicAdmin.topics().map(topics => (topicAdmin.connectionProps, topics))
          }
          .flatMap {
            case (bkProps, topicInfos) =>
              fileStore.fileInfo(data.jarKey).map(_.url).flatMap { url =>
                // check the require fields
                assertParameters(data, topicInfos)
                nodeCollie
                  .nodes()
                  .map { all =>
                    if (CommonUtils.isEmpty(data.nodeNames.asJava)) {
                      // Check instance first
                      // Here we will check the following conditions:
                      // 1. instance should be positive
                      // 2. available nodes should be bigger than instance (one node runs one instance)
                      if (all.size < data.instances)
                        throw new IllegalArgumentException(
                          s"cannot run streamApp. expect: ${data.instances}, actual: ${all.size}")
                      Random.shuffle(all).take(CommonUtils.requirePositiveInt(data.instances)).toSet
                    } else
                      // if require node name is not in nodeCollie, do not take that node
                      CommonUtils.requireNonEmpty(all.filter(n => data.nodeNames.contains(n.name)).asJava).asScala.toSet
                  }
                  .flatMap(nodes => {
                    import DefaultJsonProtocol._
                    clusterCollie.streamCollie.creator
                      .clusterName(data.name)
                      .nodeNames(nodes.map(_.name))
                      .imageName(IMAGE_NAME_DEFAULT)
                      .jarUrl(url.toString)
                      .jmxPort(data.settings(DefaultConfigs.JMX_PORT_DEFINITION.key()).convertTo[Int])
                      // these settings will send to container environment
                      // we convert all value to string for convenient
                      .settings(data.settings.map {
                        case (k, v) =>
                          k -> (v match {
                            case JsString(value) => value
                            case JsArray(arr) =>
                              arr.map(_.convertTo[String]).mkString(",")
                            case _ => v.toString()
                          })
                      } + (DefaultConfigs.BROKER_DEFINITION.key() -> bkProps)
                        + (DefaultConfigs.EXACTLY_ONCE_DEFINITION.key() -> data.exactlyOnce.toString))
                      .threadPool(executionContext)
                      .create()
                  })
              }
          }
          .map(_ => Unit)
    }

  private[this] def hookBeforeStop: HookOfAction = (_, _, _) => Future.unit

  private[this] def hookOfGroup: HookOfGroup = _ => GROUP_DEFAULT

  def apply(implicit store: DataStore,
            adminCleaner: AdminCleaner,
            nodeCollie: NodeCollie,
            clusterCollie: ClusterCollie,
            workerCollie: WorkerCollie,
            brokerCollie: BrokerCollie,
            fileStore: FileStore,
            meterCache: MeterCache,
            executionContext: ExecutionContext): server.Route =
    RouteUtils.route(
      root = STREAM_PREFIX_PATH,
      hookOfGroup = hookOfGroup,
      hookOfCreation = hookOfCreation,
      hookOfUpdate = hookOfUpdate,
      hookBeforeDelete = hookBeforeDelete,
      hookOfGet = hookOfGet,
      hookOfList = hookOfList,
      collie = clusterCollie.streamCollie,
      hookOfStart = hookOfStart,
      hookBeforeStop = hookBeforeStop
    )
}
