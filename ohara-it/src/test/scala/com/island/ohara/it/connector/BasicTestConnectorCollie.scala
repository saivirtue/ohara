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

package com.island.ohara.it.connector

import java.io.File
import java.sql.{Statement, Timestamp}

import com.island.ohara.client.configurator.v0.FileInfoApi.FileInfo
import com.island.ohara.client.configurator.v0.NodeApi.Node
import com.island.ohara.client.configurator.v0.QueryApi.RdbColumn
import com.island.ohara.client.configurator.v0.WorkerApi.WorkerClusterInfo
import com.island.ohara.client.configurator.v0.{BrokerApi, ContainerApi, FileInfoApi, WorkerApi, ZookeeperApi}
import com.island.ohara.client.database.DatabaseClient
import com.island.ohara.client.kafka.WorkerClient
import com.island.ohara.common.data.{Row, Serializer}
import com.island.ohara.common.setting.{ConnectorKey, ObjectKey, TopicKey}
import com.island.ohara.common.util.{CommonUtils, Releasable}
import com.island.ohara.configurator.Configurator
import com.island.ohara.connector.jdbc.source.{JDBCSourceConnector, JDBCSourceConnectorConfig}
import com.island.ohara.it.IntegrationTest
import com.island.ohara.it.agent.ClusterNameHolder
import com.island.ohara.kafka.Consumer
import com.island.ohara.kafka.Consumer.Record
import com.island.ohara.kafka.connector.TaskSetting
import com.typesafe.scalalogging.Logger
import org.junit.{After, Before, Test}
import org.scalatest.Matchers

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

abstract class BasicTestConnectorCollie extends IntegrationTest with Matchers {
  private[this] val log = Logger(classOf[BasicTestConnectorCollie])
  private[this] val JAR_FOLDER_KEY: String = "ohara.it.jar.folder"
  private[this] val jarFolderPath = sys.env.getOrElse(JAR_FOLDER_KEY, "/jar")

  protected def nodes: Seq[Node]

  protected def nameHolder: ClusterNameHolder

  private[this] final val group: String = com.island.ohara.client.configurator.v0.GROUP_DEFAULT

  private[this] val connectorKey = ConnectorKey.of(CommonUtils.randomString(5), "JDBC-Source-Connector-Test")
  private[this] val topicKey = TopicKey.of(CommonUtils.randomString(5), CommonUtils.randomString(5))

  protected def tableName(): String
  protected def columnPrefixName(): String
  private[this] var timestampColumn: String = _

  private[this] var client: DatabaseClient = _

  private[this] var jdbcJarFileInfo: FileInfo = _

  protected def dbUrl(): Option[String]
  protected def dbUserName(): Option[String]
  protected def dbPassword(): Option[String]
  protected def dbName(): String
  protected def insertTableSQL(tableName: String, columns: Seq[String], value: Int): String
  protected def createor(): Unit

  /**
    * This function for setting database JDBC jar file name.
    * from local upload to configurator server for connector worker container to download use.
    * @return JDBC driver file name
    */
  protected def jdbcDriverJarFileName(): String

  protected def configurator: Configurator

  private[this] def zkApi = ZookeeperApi.access.hostname(configurator.hostname).port(configurator.port)

  private[this] def bkApi = BrokerApi.access.hostname(configurator.hostname).port(configurator.port)

  private[this] def wkApi = WorkerApi.access.hostname(configurator.hostname).port(configurator.port)

  private[this] def containerApi = ContainerApi.access.hostname(configurator.hostname).port(configurator.port)

  @Before
  final def setup(): Unit = {
    checkDataBaseInfo() //Check db info
    createor()
    uploadJDBCJarToConfigurator() //For upload JDBC jar

    // Create database client
    client = DatabaseClient.builder.url(dbUrl().get).user(dbUserName().get).password(dbPassword().get).build

    // Create table
    val columns = (1 to 3).map(x => s"${columnPrefixName()}$x")
    timestampColumn = columns(0)

    val column1 = RdbColumn(columns(0), "TIMESTAMP", false)
    val column2 = RdbColumn(columns(1), "varchar(45)", false)
    val column3 = RdbColumn(columns(2), "integer", true)
    client.createTable(tableName(), Seq(column1, column2, column3))

    // Insert data in the table
    val statement: Statement = client.connection.createStatement()
    (1 to 100).foreach(i => {
      statement.execute(insertTableSQL(tableName(), columns, i))
    })
  }

  @Test
  def testJDBCSourceConnector(): Unit = {
    val zkCluster = result(
      zk_create(
        clusterName = generateClusterName(),
        clientPort = CommonUtils.availablePort(),
        electionPort = CommonUtils.availablePort(),
        peerPort = CommonUtils.availablePort(),
        nodeNames = Set(nodes.head.name)
      ))
    result(zk_start(zkCluster.name))
    assertCluster(() => result(zk_clusters()), () => result(zk_containers(zkCluster.name)), zkCluster.name)
    val bkCluster = result(
      bk_create(
        clusterName = generateClusterName(),
        clientPort = CommonUtils.availablePort(),
        exporterPort = CommonUtils.availablePort(),
        jmxPort = CommonUtils.availablePort(),
        zkClusterKey = zkCluster.key,
        nodeNames = Set(nodes.head.name)
      ))
    result(bk_start(bkCluster.name))
    assertCluster(() => result(bk_clusters()), () => result(bk_containers(bkCluster.name)), bkCluster.name)
    log.info("[WORKER] start to test worker")
    val nodeName = nodes.head.name
    val clusterName = generateClusterName()
    result(wk_exist(clusterName)) shouldBe false
    log.info("[WORKER] verify:nonExists done")
    val clientPort = CommonUtils.availablePort()
    val jmxPort = CommonUtils.availablePort()
    log.info("[WORKER] create ...")
    val wkCluster = result(
      wk_create(
        clusterName = clusterName,
        clientPort = clientPort,
        jmxPort = jmxPort,
        bkClusterName = bkCluster.name,
        nodeNames = Set(nodeName)
      ))
    log.info("[WORKER] create done")
    result(wk_start(wkCluster.name))
    log.info("[WORKER] start done")
    assertCluster(() => result(wk_clusters()), () => result(wk_containers(wkCluster.name)), wkCluster.name)
    log.info("[WORKER] verify:create done")
    result(wk_exist(wkCluster.name)) shouldBe true
    log.info("[WORKER] verify:exist done")
    // we can't assume the size since other tests may create zk cluster at the same time
    result(wk_clusters()).isEmpty shouldBe false
    testConnectors(wkCluster)

    runningJDBCSourceConnector(wkCluster.connectionProps)
    checkTopicData(bkCluster.connectionProps, topicKey.topicNameOnKafka())

    result(wk_stop(clusterName))
    await(() => {
      // In configurator mode: clusters() will return the "stopped list" in normal case
      // In collie mode: clusters() will return the "cluster list except stop one" in normal case
      // we should consider these two cases by case...
      val clusters = result(wk_clusters())
      !clusters.map(_.name).contains(clusterName) || clusters.find(_.name == clusterName).get.state.isEmpty
    })
    // the cluster is stopped actually, delete the data
    wk_delete(clusterName)
  }

  private[this] def runningJDBCSourceConnector(workerConnProps: String): Unit =
    result(
      WorkerClient(workerConnProps)
        .connectorCreator()
        .connectorKey(connectorKey)
        .connectorClass(classOf[JDBCSourceConnector])
        .topicKey(topicKey)
        .numberOfTasks(1)
        .settings(props().toMap)
        .create())

  private[this] def checkTopicData(brokers: String, topicNameOnKafka: String): Unit = {
    val consumer =
      Consumer
        .builder()
        .topicName(topicNameOnKafka)
        .offsetFromBegin()
        .connectionProps(brokers)
        .keySerializer(Serializer.ROW)
        .valueSerializer(Serializer.BYTES)
        .build()
    try {
      val record: Seq[Record[Row, Array[Byte]]] = consumer.poll(java.time.Duration.ofSeconds(50), 100).asScala
      record.size shouldBe 100

      record.head.key.get.cell(0).value.asInstanceOf[Timestamp].getTime shouldBe 1535760000000L
      record.head.key.get.cell(1).value shouldBe "a1"
      record.head.key.get.cell(2).value shouldBe 1

      record.last.key.get.cell(0).value.asInstanceOf[Timestamp].getTime shouldBe 1535760000000L
      record.last.key.get.cell(1).value shouldBe "a100"
      record.last.key.get.cell(2).value shouldBe 100
    } finally {
      consumer.close()
    }
  }

  private[this] def uploadJDBCJarToConfigurator(): Unit = {
    val jarApi: FileInfoApi.Access = FileInfoApi.access.hostname(configurator.hostname).port(configurator.port)
    val jar = new File(CommonUtils.path(jarFolderPath, jdbcDriverJarFileName()))
    jdbcJarFileInfo = result(jarApi.request.file(jar).upload())
  }

  private[this] def generateClusterName(): String = nameHolder.generateClusterName()

  private[this] def zk_clusters(): Future[Seq[ZookeeperApi.ZookeeperClusterInfo]] =
    zkApi.list()

  private[this] def zk_containers(clusterName: String): Future[Seq[ContainerApi.ContainerInfo]] =
    containerApi.get(ObjectKey.of("default", clusterName)).map(_.flatMap(_.containers))

  private[this] def zk_start(clusterName: String): Future[Unit] = zkApi.start(ObjectKey.of(group, clusterName))

  private[this] def zk_create(clusterName: String,
                              clientPort: Int,
                              electionPort: Int,
                              peerPort: Int,
                              nodeNames: Set[String]): Future[ZookeeperApi.ZookeeperClusterInfo] =
    zkApi.request
      .name(clusterName)
      .group(group)
      .clientPort(clientPort)
      .electionPort(electionPort)
      .peerPort(peerPort)
      .nodeNames(nodeNames)
      .create()

  private[this] def bk_start(clusterName: String): Future[Unit] = bkApi.start(ObjectKey.of(group, clusterName))

  private[this] def bk_clusters(): Future[Seq[BrokerApi.BrokerClusterInfo]] = bkApi.list()

  private[this] def bk_containers(clusterName: String): Future[Seq[ContainerApi.ContainerInfo]] =
    containerApi.get(ObjectKey.of("default", clusterName)).map(_.flatMap(_.containers))

  private[this] def bk_create(clusterName: String,
                              clientPort: Int,
                              exporterPort: Int,
                              jmxPort: Int,
                              zkClusterKey: ObjectKey,
                              nodeNames: Set[String]): Future[BrokerApi.BrokerClusterInfo] =
    bkApi.request
      .name(clusterName)
      .group(group)
      .clientPort(clientPort)
      .exporterPort(exporterPort)
      .jmxPort(jmxPort)
      .zookeeperClusterKey(zkClusterKey)
      .nodeNames(nodeNames)
      .create()

  private[this] def wk_start(clusterName: String): Future[Unit] = wkApi.start(ObjectKey.of(group, clusterName))

  private[this] def wk_stop(clusterName: String): Future[Unit] =
    wkApi.forceStop(ObjectKey.of(group, clusterName)).map(_ => Unit)

  private[this] def wk_containers(clusterName: String): Future[Seq[ContainerApi.ContainerInfo]] =
    containerApi.get(ObjectKey.of("default", clusterName)).map(_.flatMap(_.containers))

  private[this] def wk_exist(clusterName: String): Future[Boolean] = wkApi.list().map(_.exists(_.name == clusterName))

  private[this] def wk_delete(clusterName: String): Future[Unit] = wkApi.delete(ObjectKey.of(group, clusterName))

  private[this] def wk_clusters(): Future[Seq[WorkerApi.WorkerClusterInfo]] = wkApi.list()

  private[this] def wk_create(clusterName: String,
                              clientPort: Int,
                              jmxPort: Int,
                              bkClusterName: String,
                              nodeNames: Set[String]): Future[WorkerApi.WorkerClusterInfo] =
    wkApi.request
      .name(clusterName)
      .group(group)
      .clientPort(clientPort)
      .jmxPort(jmxPort)
      .brokerClusterName(bkClusterName)
      .nodeNames(nodeNames)
      .jarInfos(Seq(jdbcJarFileInfo))
      .create()

  private[this] def checkDataBaseInfo(): Unit = {
    if (dbUrl().isEmpty || dbUserName().isEmpty || dbPassword().isEmpty)
      skipTest(s"Skip the JDBC source connector test, Please setting dbURL, dbUserName and dbPassword")

    if (jarFolderPath.isEmpty)
      skipTest(s"Please setting jdbc jar folder path.")
  }

  private[this] def testConnectors(cluster: WorkerClusterInfo): Unit =
    await(
      () =>
        try {
          log.info(s"worker node head: ${cluster.nodeNames.head}:${cluster.clientPort}")
          result(WorkerClient(s"${cluster.nodeNames.head}:${cluster.clientPort}").connectorDefinitions()).nonEmpty
        } catch {
          case e: Throwable =>
            log.info(s"[WORKER] worker cluster:${cluster.name} is starting ... retry", e)
            false
      }
    )

  private[this] def props(): JDBCSourceConnectorConfig =
    JDBCSourceConnectorConfig(
      TaskSetting.of(Map(
        "source.db.url" -> dbUrl().get,
        "source.db.username" -> dbUserName().get,
        "source.db.password" -> dbPassword().get,
        "source.table.name" -> tableName,
        "source.timestamp.column.name" -> timestampColumn,
        "source.schema.pattern" -> "TUSER"
      ).asJava))

  @After
  def afterTest(): Unit = {
    if (client != null) {
      val statement: Statement = client.connection.createStatement()
      statement.execute(s"drop table ${tableName()}")
    }
    Releasable.close(client)
    Releasable.close(configurator)
    Releasable.close(nameHolder)
  }
}
