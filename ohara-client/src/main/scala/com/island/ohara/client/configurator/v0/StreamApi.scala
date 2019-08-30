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

package com.island.ohara.client.configurator.v0
import com.island.ohara.client.configurator.v0.FileInfoApi.FileInfo
import com.island.ohara.client.configurator.v0.MetricsApi.Metrics
import com.island.ohara.common.annotations.{Optional, VisibleForTesting}
import com.island.ohara.common.setting.{ObjectKey, TopicKey}
import com.island.ohara.common.util.{CommonUtils, VersionUtils}
import com.island.ohara.streams.config.StreamDefUtils
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

object StreamApi {

  /**
    * container name is controlled by streamRoute, the service name here use five words was ok.
    * TODO: "stream" is derpecated now. see https://github.com/oharastream/ohara/issues/2115
    */
  val STREAM_SERVICE_NAME: String = "stream"
  // TODO: TODO: "stream" is derpecated now. see https://github.com/oharastream/ohara/issues/2115
  val STREAM_PREFIX_PATH: String = STREAM_SERVICE_NAME
  val STREAMS_PREFIX_PATH: String = "streams"

  /**
    * The default value of group for this API.
    */
  val GROUP_DEFAULT: String = com.island.ohara.client.configurator.v0.GROUP_DEFAULT

  val LIMIT_OF_NAME_LENGTH: Int = ZookeeperApi.LIMIT_OF_NAME_LENGTH

  /**
    * StreamApp Docker Image name
    */
  final val IMAGE_NAME_DEFAULT: String = s"oharastream/streamapp:${VersionUtils.VERSION}"

  final case class Creation(settings: Map[String, JsValue]) extends ClusterCreationRequest {

    /**
      * Convert all json value to plain string. It keeps the json format but all stuff are in string.
      */
    def plain: Map[String, String] = noJsNull(settings).map {
      case (k, v) =>
        k -> (v match {
          case JsString(value) => value
          case _               => v.toString()
        })
    }

    def brokerClusterName: Option[String] = plain.get(StreamDefUtils.BROKER_CLUSTER_NAME_DEFINITION.key())

    override def name: String = plain(StreamDefUtils.NAME_DEFINITION.key())

    override def group: String = plain(StreamDefUtils.GROUP_DEFINITION.key())

    override def imageName: String = plain(StreamDefUtils.IMAGE_NAME_DEFINITION.key())

    override def nodeNames: Set[String] =
      noJsNull(settings)(StreamDefUtils.NODE_NAMES_DEFINITION.key()).convertTo[Seq[String]].toSet

    override def tags: Map[String, JsValue] = noJsNull(settings)
      .find(_._1 == StreamDefUtils.TAGS_DEFINITION.key())
      .map(_._2)
      .map {
        case s: JsObject => s.fields
        case other: JsValue =>
          throw new IllegalArgumentException(s"the type of tags should be JsObject, actual type is ${other.getClass}")
      }
      .getOrElse(Map.empty)

    override def ports: Set[Int] = Set(plain(StreamDefUtils.JMX_PORT_DEFINITION.key()).toInt)

    def jarKey: Option[ObjectKey] =
      noJsNull(settings).get(StreamDefUtils.JAR_KEY_DEFINITION.key()).map(OBJECT_KEY_FORMAT.read)

    def jmxPort: Int = plain(StreamDefUtils.JMX_PORT_DEFINITION.key()).toInt

    def from: Set[TopicKey] =
      noJsNull(settings)(StreamDefUtils.FROM_TOPIC_KEYS_DEFINITION.key()).convertTo[Set[TopicKey]]

    def to: Set[TopicKey] =
      noJsNull(settings)(StreamDefUtils.TO_TOPIC_KEYS_DEFINITION.key()).convertTo[Set[TopicKey]]

    //TODO remove this field after #2288
    def instances: Option[Int] = plain.get(StreamDefUtils.INSTANCES_DEFINITION.key()).map(_.toInt)
  }
  implicit val STREAM_CREATION_JSON_FORMAT: OharaJsonFormat[Creation] =
    JsonRefiner[Creation]
      .format(new RootJsonFormat[Creation] {
        override def write(obj: Creation): JsValue = JsObject(noJsNull(obj.settings))
        override def read(json: JsValue): Creation = Creation(json.asJsObject.fields)
      })
      // the default value
      .nullToString(StreamDefUtils.IMAGE_NAME_DEFINITION.key(), IMAGE_NAME_DEFAULT)
      .nullToRandomPort(StreamDefUtils.JMX_PORT_DEFINITION.key())
      //TODO remove this default value after #2288
      .nullToEmptyArray(StreamDefUtils.NODE_NAMES_DEFINITION.key())
      .nullToEmptyArray(StreamDefUtils.FROM_TOPIC_KEYS_DEFINITION.key())
      .nullToEmptyArray(StreamDefUtils.TO_TOPIC_KEYS_DEFINITION.key())
      .nullToString(StreamDefUtils.NAME_DEFINITION.key(), () => CommonUtils.randomString(LIMIT_OF_NAME_LENGTH))
      .nullToString(StreamDefUtils.GROUP_DEFINITION.key(), GROUP_DEFAULT)
      .nullToEmptyObject(TAGS_KEY)
      // restrict rules
      .requireBindPort(StreamDefUtils.JMX_PORT_DEFINITION.key())
      .requirePositiveNumber(StreamDefUtils.INSTANCES_DEFINITION.key())
      .rejectEmptyString()
      .arrayRestriction("nodeNames")
      .rejectKeyword(START_COMMAND)
      .rejectKeyword(STOP_COMMAND)
      .toRefiner
      .stringRestriction(NAME_KEY)
      .withNumber()
      .withLowerCase()
      .withLengthLimit(LIMIT_OF_NAME_LENGTH)
      .toRefiner
      .refine

  final case class Update(settings: Map[String, JsValue]) extends ClusterUpdateRequest {

    def brokerClusterName: Option[String] =
      noJsNull(settings).get(StreamDefUtils.BROKER_CLUSTER_NAME_DEFINITION.key()).map(_.convertTo[String])

    def imageName: Option[String] =
      noJsNull(settings).get(StreamDefUtils.IMAGE_NAME_DEFINITION.key()).map(_.convertTo[String])

    def jarKey: Option[ObjectKey] =
      noJsNull(settings).get(StreamDefUtils.JAR_KEY_DEFINITION.key()).map(OBJECT_KEY_FORMAT.read)

    def jmxPort: Option[Int] = noJsNull(settings).get(StreamDefUtils.JMX_PORT_DEFINITION.key()).map(_.convertTo[Int])

    def from: Option[Set[TopicKey]] =
      noJsNull(settings).get(StreamDefUtils.FROM_TOPIC_KEYS_DEFINITION.key()).map(_.convertTo[Set[TopicKey]])

    def to: Option[Set[TopicKey]] =
      noJsNull(settings).get(StreamDefUtils.TO_TOPIC_KEYS_DEFINITION.key()).map(_.convertTo[Set[TopicKey]])

    def nodeNames: Option[Set[String]] =
      noJsNull(settings).get(StreamDefUtils.NODE_NAMES_DEFINITION.key()).map(_.convertTo[Seq[String]].toSet)

    //TODO remove this field after #2288
    def instances: Option[Int] =
      noJsNull(settings).get(StreamDefUtils.INSTANCES_DEFINITION.key()).map(_.convertTo[Int])

    def tags: Option[Map[String, JsValue]] =
      noJsNull(settings).get(StreamDefUtils.TAGS_DEFINITION.key()).map {
        case s: JsObject => s.fields
        case other: JsValue =>
          throw new IllegalArgumentException(s"the type of tags should be JsObject, actual type is ${other.getClass}")
      }
  }
  implicit val STREAM_UPDATE_JSON_FORMAT: OharaJsonFormat[Update] =
    JsonRefiner[Update]
      .format(new RootJsonFormat[Update] {
        override def write(obj: Update): JsValue = JsObject(noJsNull(obj.settings))
        override def read(json: JsValue): Update = Update(json.asJsObject.fields)
      })
      .arrayRestriction("nodeNames")
      .rejectKeyword(START_COMMAND)
      .rejectKeyword(STOP_COMMAND)
      .rejectEmpty()
      .toRefiner
      .requireBindPort(StreamDefUtils.JMX_PORT_DEFINITION.key())
      .requirePositiveNumber(StreamDefUtils.INSTANCES_DEFINITION.key())
      .rejectEmptyString()
      .refine

  /**
    * The Stream Cluster Information stored in configurator
    *
    * @param settings streamApp key-value pair settings
    * @param nodeNames node list of streamApp running container
    * @param deadNodes dead node list of the exited containers from this cluster
    * @param state the state of streamApp (stopped streamApp does not have this field)
    * @param error the error message if the state was failed to fetch
    * @param metrics the metrics bean
    * @param lastModified this data change time
    */
  final case class StreamClusterInfo(settings: Map[String, JsValue],
                                     // TODO: move nodeNames to settings since it is a "setting" from user ... by chia
                                     // https://github.com/oharastream/ohara/issues/2438
                                     nodeNames: Set[String],
                                     deadNodes: Set[String],
                                     state: Option[String],
                                     error: Option[String],
                                     metrics: Metrics,
                                     lastModified: Long)
      extends ClusterInfo {

    /**
      * Convert all json value to plain string. It keeps the json format but all stuff are in string.
      */
    def plain: Map[String, String] = noJsNull(settings).map {
      case (k, v) =>
        k -> (v match {
          case JsString(value) => value
          case _               => v.toString()
        })
    }

    // streamapp does not support to define group
    override def group: String = GROUP_DEFAULT
    override def name: String = plain(StreamDefUtils.NAME_DEFINITION.key())
    override def kind: String = STREAM_SERVICE_NAME
    override def ports: Set[Int] = Set(jmxPort)
    override def tags: Map[String, JsValue] =
      noJsNull(settings)(StreamDefUtils.TAGS_DEFINITION.key()).asJsObject.fields

    def imageName: String = plain(StreamDefUtils.IMAGE_NAME_DEFINITION.key())
    // TODO this field is deprecated and should be removed in #2288
    def instances: Int = plain(StreamDefUtils.INSTANCES_DEFINITION.key()).toInt

    /**
      * Return the key of explicit value. Otherwise, return the key of jar info.
      * Normally, the key should be equal to jar info
      * @return key of jar
      */
    def jarKey: ObjectKey =
      noJsNull(settings).get(StreamDefUtils.JAR_KEY_DEFINITION.key()).map(OBJECT_KEY_FORMAT.read).getOrElse(jarInfo.key)

    def jarInfo: FileInfo =
      FileInfoApi.FILE_INFO_JSON_FORMAT.read(noJsNull(settings)(StreamDefUtils.JAR_INFO_DEFINITION.key()))

    def brokerClusterName: String = plain(StreamDefUtils.BROKER_CLUSTER_NAME_DEFINITION.key())
    def from: Set[TopicKey] =
      noJsNull(settings)(StreamDefUtils.FROM_TOPIC_KEYS_DEFINITION.key()).convertTo[Set[TopicKey]]
    def to: Set[TopicKey] =
      noJsNull(settings)(StreamDefUtils.TO_TOPIC_KEYS_DEFINITION.key()).convertTo[Set[TopicKey]]
    def jmxPort: Int = plain(StreamDefUtils.JMX_PORT_DEFINITION.key()).toInt
    // TODO remove this default value after we could handle from UI
    def exactlyOnce: Boolean = false
    def className: Option[String] = plain.get(StreamDefUtils.CLASS_NAME_DEFINITION.key())

    override def clone(nodeNames: Set[String],
                       deadNodes: Set[String],
                       state: Option[String],
                       error: Option[String],
                       metrics: Metrics,
                       tags: Map[String, JsValue]): StreamClusterInfo = copy(
      settings = this.settings ++ Map(
        StreamDefUtils.NODE_NAMES_DEFINITION.key() -> JsArray(nodeNames.map(JsString(_)).toVector),
        StreamDefUtils.TAGS_DEFINITION.key() -> JsObject(tags)),
      nodeNames = nodeNames,
      deadNodes = deadNodes,
      state = state,
      error = error,
      metrics = metrics
    )
  }
  private[ohara] implicit val STREAM_CLUSTER_INFO_JSON_FORMAT: OharaJsonFormat[StreamClusterInfo] =
    JsonRefiner[StreamClusterInfo]
      .format(new RootJsonFormat[StreamClusterInfo] {
        private[this] val format = jsonFormat7(StreamClusterInfo)
        override def read(json: JsValue): StreamClusterInfo = format.read(json)
        override def write(obj: StreamClusterInfo): JsValue =
          JsObject(
            noJsNull(
              format.write(obj).asJsObject.fields ++
                Map(GROUP_KEY -> JsString(GROUP_DEFAULT), NAME_KEY -> obj.settings.getOrElse(NAME_KEY, JsNull))
            ))
      })
      .refine

  sealed trait Request {
    @Optional("default name is a random string. But it is required in updating")
    def name(name: String): Request =
      setting(StreamDefUtils.NAME_DEFINITION.key(), JsString(CommonUtils.requireNonEmpty(name)))

    @Optional("the default image is IMAGE_NAME_DEFAULT")
    def imageName(imageName: String): Request =
      setting(StreamDefUtils.IMAGE_NAME_DEFINITION.key(), JsString(CommonUtils.requireNonEmpty(imageName)))
    def jarKey(jarKey: ObjectKey): Request =
      setting(StreamDefUtils.JAR_KEY_DEFINITION.key(), ObjectKey.toJsonString(jarKey).parseJson)
    def fromTopicKey(fromTopicKey: TopicKey): Request = fromTopicKeys(Set(fromTopicKey))
    def fromTopicKeys(fromTopicKeys: Set[TopicKey]): Request =
      setting(StreamDefUtils.FROM_TOPIC_KEYS_DEFINITION.key(),
              JsArray(fromTopicKeys.map(TOPIC_KEY_FORMAT.write).toVector))
    def toTopicKey(toTopicKey: TopicKey): Request = toTopicKeys(Set(toTopicKey))
    def toTopicKeys(toTopicKeys: Set[TopicKey]): Request =
      setting(StreamDefUtils.TO_TOPIC_KEYS_DEFINITION.key(), JsArray(toTopicKeys.map(TOPIC_KEY_FORMAT.write).toVector))
    @Optional("server picks up a broker cluster for you if broker cluster name is empty")
    def brokerClusterName(brokerClusterName: String): Request = setting(
      StreamDefUtils.BROKER_CLUSTER_NAME_DEFINITION.key(),
      JsString(CommonUtils.requireNonEmpty(brokerClusterName)))
    @Optional("the default port is random")
    def jmxPort(jmxPort: Int): Request =
      setting(StreamDefUtils.JMX_PORT_DEFINITION.key(), JsNumber(CommonUtils.requireConnectionPort(jmxPort)))
    @Optional("you should not set both nodeNames and instances")
    //TODO "This should be removed after #2288"
    def instances(instances: Int): Request =
      setting(StreamDefUtils.INSTANCES_DEFINITION.key(), JsNumber(CommonUtils.requirePositiveInt(instances)))
    @Optional("you should not set both nodeNames and instances")
    def nodeNames(nodeNames: Set[String]): Request = setting(
      StreamDefUtils.NODE_NAMES_DEFINITION.key(),
      JsArray(CommonUtils.requireNonEmpty(nodeNames.asJava).asScala.map(JsString(_)).toVector))
    @Optional("default value is empty array in creation and None in update")
    def tags(tags: Map[String, JsValue]): Request = setting(StreamDefUtils.TAGS_DEFINITION.key(), JsObject(tags))

    @Optional("extra settings is empty by default")
    def setting(key: String, value: JsValue): Request = settings(Map(key -> value))
    @Optional("extra settings is empty by default")
    def settings(settings: Map[String, JsValue]): Request

    /**
      * generate POST request
      *
      * @param executionContext execution context
      * @return created data
      */
    def create()(implicit executionContext: ExecutionContext): Future[StreamClusterInfo]

    /**
      * generate the PUT request
      *
      * @param executionContext execution context
      * @return updated/created data
      */
    def update()(implicit executionContext: ExecutionContext): Future[StreamClusterInfo]

    /**
      * for testing only
      * @return the payload of creation
      */
    @VisibleForTesting
    private[v0] def creation: Creation

    /**
      * for testing only
      * @return the payload of update
      */
    @VisibleForTesting
    private[v0] def update: Update
  }

  final class Access extends ClusterAccess[StreamClusterInfo](STREAM_PREFIX_PATH, GROUP_DEFAULT) {

    def request: Request = new Request {
      private[this] val settings: mutable.Map[String, JsValue] = mutable.Map[String, JsValue]()

      override def settings(settings: Map[String, JsValue]): Request = {
        this.settings ++= CommonUtils.requireNonEmpty(settings.asJava).asScala.toMap
        this
      }
      override private[v0] def creation: Creation =
        // auto-complete the creation via our refiner
        STREAM_CREATION_JSON_FORMAT.read(STREAM_CREATION_JSON_FORMAT.write(Creation(update.settings)))

      override private[v0] def update: Update =
        // auto-complete the update via our refiner
        STREAM_UPDATE_JSON_FORMAT.read(STREAM_UPDATE_JSON_FORMAT.write(Update(noJsNull(settings.toMap))))

      override def create()(implicit executionContext: ExecutionContext): Future[StreamClusterInfo] =
        exec.post[Creation, StreamClusterInfo, ErrorApi.Error](
          url,
          creation
        )

      override def update()(implicit executionContext: ExecutionContext): Future[StreamClusterInfo] = {
        exec.put[Update, StreamClusterInfo, ErrorApi.Error](
          s"$url/${CommonUtils.requireNonEmpty(settings(StreamDefUtils.NAME_DEFINITION.key()).convertTo[String])}",
          update
        )
      }
    }
  }

  def access: Access = new Access
}
