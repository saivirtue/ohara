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

import java.util.Objects

import com.island.ohara.client.configurator.v0.MetricsApi.Metrics
import com.island.ohara.common.annotations.Optional
import com.island.ohara.common.setting.{ObjectKey, SettingDef}
import com.island.ohara.common.util.{CommonUtils, VersionUtils}
import spray.json.DefaultJsonProtocol._
import spray.json.{JsNumber, JsObject, JsValue, RootJsonFormat}

import scala.concurrent.{ExecutionContext, Future}

object BrokerApi {

  val BROKER_PREFIX_PATH: String = "brokers"

  val BROKER_SERVICE_NAME: String = "bk"

  /**
    * the default docker image used to run containers of broker cluster
    */
  val IMAGE_NAME_DEFAULT: String = s"oharastream/broker:${VersionUtils.VERSION}"

  //------------------------ The key name list in settings field ---------------------------------/
  private[ohara] val CLIENT_PORT_KEY: String = "clientPort"
  private[this] val EXPORTER_PORT_KEY: String = "exporterPort"
  private[this] val JMX_PORT_KEY: String = "jmxPort"
  private[ohara] val ADVERTISED_HOSTNAME_KEY: String = "advertisedHostname"
  private[ohara] val ADVERTISED_CLIENT_PORT_KEY: String = "advertisedClientPort"
  private[ohara] val ID_KEY: String = "brokerId"
  private[ohara] val DATA_DIRECTORY_KEY: String = "dataDir"
  private[ohara] val ZOOKEEPERS_KEY: String = "zookeepers"
  private[ohara] val JMX_HOSTNAME_KEY: String = "jmxHostname"

  /**
    * internal key used to save the zookeeper cluster key.
    * All nodes of broker cluster should have this environment variable.
    */
  private[ohara] val ZOOKEEPER_CLUSTER_KEY_KEY: String = "zookeeperClusterKey"

  // TODO: remove this stale field (see https://github.com/oharastream/ohara/issues/2731)
  private[this] val ZOOKEEPER_CLUSTER_NAME_KEY: String = "zookeeperClusterName"

  final class Creation(val settings: Map[String, JsValue]) extends ClusterCreation {

    /**
      * reuse the parser from Update.
      * @param settings settings
      * @return update
      */
    private[this] implicit def update(settings: Map[String, JsValue]): Updating = new Updating(noJsNull(settings))
    // the name and group fields are used to identify zookeeper cluster object
    // we should give them default value in JsonRefiner
    override def name: String = settings.name.get
    override def group: String = settings.group.get
    // helper method to get the key
    private[ohara] def key: ObjectKey = ObjectKey.of(group, name)

    override def imageName: String = settings.imageName.get
    override def nodeNames: Set[String] = settings.nodeNames.get
    override def ports: Set[Int] = Set(clientPort, exporterPort, jmxPort)
    override def tags: Map[String, JsValue] = settings.tags.get

    def exporterPort: Int = settings.exporterPort.get
    def clientPort: Int = settings.clientPort.get
    def jmxPort: Int = settings.jmxPort.get
    def zookeeperClusterKey: Option[ObjectKey] = settings.zookeeperClusterKey
  }

  /**
    * exposed to configurator
    */
  private[ohara] implicit val BROKER_CREATION_JSON_FORMAT: OharaJsonFormat[Creation] =
    basicRulesOfCreation[Creation](IMAGE_NAME_DEFAULT)
      .format(new RootJsonFormat[Creation] {
        override def write(obj: Creation): JsValue = JsObject(noJsNull(obj.settings))
        override def read(json: JsValue): Creation = new Creation(json.asJsObject.fields)
      })
      .nullToRandomPort(CLIENT_PORT_KEY)
      .requireBindPort(CLIENT_PORT_KEY)
      .nullToRandomPort(EXPORTER_PORT_KEY)
      .requireBindPort(EXPORTER_PORT_KEY)
      .nullToRandomPort(JMX_PORT_KEY)
      .requireBindPort(JMX_PORT_KEY)
      .refine

  final class Updating(val settings: Map[String, JsValue]) extends ClusterUpdating {
    // We use the update parser to get the name and group
    private[BrokerApi] def name: Option[String] = noJsNull(settings).get(NAME_KEY).map(_.convertTo[String])
    private[BrokerApi] def group: Option[String] = noJsNull(settings).get(GROUP_KEY).map(_.convertTo[String])
    override def imageName: Option[String] = noJsNull(settings).get(IMAGE_NAME_KEY).map(_.convertTo[String])
    override def nodeNames: Option[Set[String]] =
      noJsNull(settings).get(NODE_NAMES_KEY).map(_.convertTo[Seq[String]].toSet)
    override def tags: Option[Map[String, JsValue]] = noJsNull(settings).get(TAGS_KEY).map {
      case s: JsObject => s.fields
      case other: JsValue =>
        throw new IllegalArgumentException(s"the type of tags should be JsObject, actual type is ${other.getClass}")
    }

    def exporterPort: Option[Int] = noJsNull(settings).get(EXPORTER_PORT_KEY).map(_.convertTo[Int])
    def clientPort: Option[Int] = noJsNull(settings).get(CLIENT_PORT_KEY).map(_.convertTo[Int])
    def jmxPort: Option[Int] = noJsNull(settings).get(JMX_PORT_KEY).map(_.convertTo[Int])

    // TODO: remove this stale field (see https://github.com/oharastream/ohara/issues/2731)
    private[this] def zookeeperClusterName: Option[String] =
      noJsNull(settings).get(ZOOKEEPER_CLUSTER_NAME_KEY).map(_.convertTo[String])

    def zookeeperClusterKey: Option[ObjectKey] = noJsNull(settings)
      .get(ZOOKEEPER_CLUSTER_KEY_KEY)
      .map(_.convertTo[ObjectKey])
      .orElse(zookeeperClusterName.map(n => ObjectKey.of(GROUP_DEFAULT, n)))
  }

  implicit val BROKER_UPDATING_JSON_FORMAT: OharaJsonFormat[Updating] =
    basicRulesOfUpdating[Updating]
      .format(new RootJsonFormat[Updating] {
        override def write(obj: Updating): JsValue = JsObject(noJsNull(obj.settings))
        override def read(json: JsValue): Updating = new Updating(json.asJsObject.fields)
      })
      .requireBindPort(CLIENT_PORT_KEY)
      .requireBindPort(EXPORTER_PORT_KEY)
      .requireBindPort(JMX_PORT_KEY)
      .refine

  final case class BrokerClusterInfo private[BrokerApi] (settings: Map[String, JsValue],
                                                         aliveNodes: Set[String],
                                                         lastModified: Long,
                                                         state: Option[String],
                                                         error: Option[String],
                                                         topicSettingDefinitions: Seq[SettingDef])
      extends ClusterInfo {

    /**
      * reuse the parser from Creation.
      * @param settings settings
      * @return creation
      */
    private[this] implicit def creation(settings: Map[String, JsValue]): Creation = new Creation(noJsNull(settings))
    override def name: String = settings.name
    override def group: String = settings.group
    override def kind: String = BROKER_SERVICE_NAME
    override def ports: Set[Int] = Set(clientPort, exporterPort, jmxPort)
    override def tags: Map[String, JsValue] = settings.tags
    def nodeNames: Set[String] = settings.nodeNames

    /**
      * the node names is not equal to "running" nodes. The connection props may reference to invalid nodes and the error
      * should be handled by the client code.
      * @return a string host_0:port,host_1:port
      */
    def connectionProps: String = if (nodeNames.isEmpty) throw new IllegalArgumentException("there is no nodes!!!")
    else nodeNames.map(n => s"$n:$clientPort").mkString(",")

    // TODO remove this duplicated fields after #2191
    def imageName: String = settings.imageName
    def exporterPort: Int = settings.exporterPort
    def clientPort: Int = settings.clientPort
    def jmxPort: Int = settings.jmxPort
    def zookeeperClusterKey: ObjectKey = settings.zookeeperClusterKey.get
    // TODO: expose the metrics for bk
    override def metrics: Metrics = Metrics.EMPTY
  }

  /**
    * exposed to configurator
    */
  private[ohara] implicit val BROKER_CLUSTER_INFO_JSON_FORMAT: OharaJsonFormat[BrokerClusterInfo] =
    JsonRefiner[BrokerClusterInfo]
      .format(new RootJsonFormat[BrokerClusterInfo] {
        private[this] val format = jsonFormat6(BrokerClusterInfo)
        override def read(json: JsValue): BrokerClusterInfo = format.read(json)
        override def write(obj: BrokerClusterInfo): JsValue =
          JsObject(noJsNull(format.write(obj).asJsObject.fields))
      })
      .refine

  /**
    * used to generate the payload and url for POST/PUT request.
    * this request is extended by collie also so it is public than sealed.
    */
  trait Request extends ClusterRequest {
    @Optional("Ignoring zookeeper cluster key enable server to match a zk for you")
    def zookeeperClusterKey(zookeeperClusterKey: ObjectKey): Request.this.type =
      setting(ZOOKEEPER_CLUSTER_KEY_KEY, OBJECT_KEY_FORMAT.write(Objects.requireNonNull(zookeeperClusterKey)))
    @Optional("the default port is random")
    def clientPort(clientPort: Int): Request.this.type =
      setting(CLIENT_PORT_KEY, JsNumber(CommonUtils.requireConnectionPort(clientPort)))
    @Optional("the default port is random")
    def exporterPort(exporterPort: Int): Request.this.type =
      setting(EXPORTER_PORT_KEY, JsNumber(CommonUtils.requireConnectionPort(exporterPort)))
    @Optional("the default port is random")
    def jmxPort(jmxPort: Int): Request.this.type =
      setting(JMX_PORT_KEY, JsNumber(CommonUtils.requireConnectionPort(jmxPort)))
    @Optional("default value is empty array in creation and None in update")
    def tags(tags: Map[String, JsValue]): Request.this.type = setting(TAGS_KEY, JsObject(tags))

    /**
      * broker information creation.
      *  Here we open the access for reusing the creation to other module
      *
      * @return the payload of creation
      */
    final def creation: Creation =
      // auto-complete the creation via our refiner
      BROKER_CREATION_JSON_FORMAT.read(BROKER_CREATION_JSON_FORMAT.write(new Creation(noJsNull(settings.toMap))))

    /**
      * for testing only
      * @return the payload of update
      */
    private[v0] final def updating: Updating =
      // auto-complete the update via our refiner
      BROKER_UPDATING_JSON_FORMAT.read(BROKER_UPDATING_JSON_FORMAT.write(new Updating(noJsNull(settings.toMap))))
  }

  /**
    * similar to Request but it has execution methods.
    */
  sealed trait ExecutableRequest extends Request {
    def create()(implicit executionContext: ExecutionContext): Future[BrokerClusterInfo]
    def update()(implicit executionContext: ExecutionContext): Future[BrokerClusterInfo]
  }

  final class Access private[BrokerApi]
      extends ClusterAccess[Creation, Updating, BrokerClusterInfo](BROKER_PREFIX_PATH) {
    def request: ExecutableRequest = new ExecutableRequest {

      override def create()(implicit executionContext: ExecutionContext): Future[BrokerClusterInfo] = post(creation)

      override def update()(implicit executionContext: ExecutionContext): Future[BrokerClusterInfo] =
        put(
          // for update request, we should use default group if it was absent
          ObjectKey.of(
            updating.group.getOrElse(GROUP_DEFAULT),
            updating.name.getOrElse(throw new IllegalArgumentException("name is required in update request"))),
          updating
        )
    }
  }

  def access: Access = new Access
}
