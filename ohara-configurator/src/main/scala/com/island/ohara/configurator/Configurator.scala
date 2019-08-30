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

package com.island.ohara.configurator

import java.util.concurrent.{ExecutionException, Executors, TimeUnit}

import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{handleRejections, path, _}
import akka.http.scaladsl.server.{ExceptionHandler, MalformedRequestContentRejection, RejectionHandler}
import akka.http.scaladsl.{Http, server}
import akka.stream.ActorMaterializer
import com.island.ohara.agent._
import com.island.ohara.agent.k8s.K8SClient
import com.island.ohara.client.HttpExecutor
import com.island.ohara.client.configurator.v0.BrokerApi.BrokerClusterInfo
import com.island.ohara.client.configurator.v0.MetricsApi.Meter
import com.island.ohara.client.configurator.v0.StreamApi.StreamClusterInfo
import com.island.ohara.client.configurator.v0.WorkerApi.WorkerClusterInfo
import com.island.ohara.client.configurator.v0._
import com.island.ohara.client.configurator.{ConfiguratorApiInfo, Data}
import com.island.ohara.common.data.Serializer
import com.island.ohara.common.util.{CommonUtils, Releasable, ReleaseOnce}
import com.island.ohara.configurator.Configurator.Mode
import com.island.ohara.configurator.file.FileStore
import com.island.ohara.configurator.route._
import com.island.ohara.configurator.store.{DataStore, MeterCache}
import com.typesafe.scalalogging.Logger
import spray.json.DeserializationException

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

/**
  * A simple impl from Configurator. This impl maintains all subclass from ohara data in a single ohara store.
  * NOTED: there are many route requiring the implicit variables so we make them be implicit in construction.
  *
  */
class Configurator private[configurator] (val hostname: String, val port: Int)(implicit val store: DataStore,
                                                                               val fileStore: FileStore,
                                                                               val nodeCollie: NodeCollie,
                                                                               val clusterCollie: ClusterCollie,
                                                                               val k8sClient: Option[K8SClient])
    extends ReleaseOnce
    with SprayJsonSupport {

  private[this] val threadMax = {
    val value = Runtime.getRuntime.availableProcessors()
    if (value <= 1)
      throw new IllegalArgumentException(
        s"I'm sorry that your machine is too weak to run Ohara Configurator." +
          s" The required number of core must be bigger than 2, but actual number is $value")
    value
  }

  private[this] val threadPool = Executors.newFixedThreadPool(threadMax)

  private[this] implicit val executionContext: ExecutionContext = ExecutionContext.fromExecutor(threadPool)

  /**
    * this timeout is used to wait the socket server to be ready to accept connection.
    */
  private[this] val initializationTimeout = 10 seconds

  /**
    * this timeout is used to
    * 1) unbind the socket server
    * 2) reject all incoming requests
    * 3) wait and then terminate in-flight requests
    * A small timeout can reduce the time to close configurator, and it is useful for testing. Perhaps we should expose this timeout to production
    * purpose. However, we have not met related use cases or bugs and hence we leave a constant timeout here.
    */
  private[this] val terminateTimeout = 3 seconds
  private[this] val cacheTimeout = 3 seconds
  private[this] val cleanupTimeout = 30 seconds

  private[configurator] def size: Int = store.size()

  private[this] val log = Logger(classOf[Configurator])

  private[this] implicit val zookeeperCollie: ZookeeperCollie = clusterCollie.zookeeperCollie
  private[this] implicit val brokerCollie: BrokerCollie = clusterCollie.brokerCollie
  private[this] implicit val workerCollie: WorkerCollie = clusterCollie.workerCollie
  private[this] implicit val streamCollie: StreamCollie = clusterCollie.streamCollie

  def mode: Mode = clusterCollie match {
    case _: com.island.ohara.agent.ssh.ClusterCollieImpl         => Mode.SSH
    case _: com.island.ohara.agent.k8s.K8SClusterCollieImpl      => Mode.K8S
    case _: com.island.ohara.configurator.fake.FakeClusterCollie => Mode.FAKE
    case _                                                       => throw new IllegalArgumentException(s"unknown cluster collie: ${clusterCollie.getClass.getName}")
  }

  private[this] def exceptionHandler(): ExceptionHandler = ExceptionHandler {
    case e @ (_: DeserializationException | _: ParsingException | _: IllegalArgumentException |
        _: NoSuchElementException) =>
      extractRequest { request =>
        log.error(s"Request to ${request.uri} with ${request.entity} is wrong", e)
        complete(StatusCodes.BadRequest -> ErrorApi.of(e))
      }
    case e: Throwable =>
      extractRequest { request =>
        log.error(s"Request to ${request.uri} with ${request.entity} could not be handled normally", e)
        complete(StatusCodes.InternalServerError -> ErrorApi.of(e))
      }
  }

  /**
    *Akka use rejection to wrap error message
    */
  private[this] def rejectionHandler(): RejectionHandler =
    RejectionHandler
      .newBuilder()
      .handle {
        // seek the true exception
        case MalformedRequestContentRejection(_, cause) if cause != null => throw cause
        case e: ExecutionException if e.getCause != null                 => throw e.getCause
      }
      .result()

  private[this] implicit val meterCache: MeterCache = {
    def brokerToMeters(brokerClusterInfo: BrokerClusterInfo): Map[String, Seq[Meter]] =
      brokerCollie.topicMeters(brokerClusterInfo).groupBy(_.topicName()).map {
        case (topicName, topicMeters) =>
          topicName -> topicMeters.map { meter =>
            Meter(
              value = meter.count(),
              unit = s"${meter.eventType()} / ${meter.rateUnit().name()}",
              document = meter.catalog.name(),
              queryTime = meter.queryTime(),
              startTime = None
            )
          }.toList // convert to serializable collection
      }
    def workerToMeters(workerClusterInfo: WorkerClusterInfo): Map[String, Seq[Meter]] =
      workerCollie.counters(workerClusterInfo).groupBy(_.group()).map {
        case (connectorId, counters) =>
          connectorId -> counters.map { counter =>
            Meter(
              value = counter.getValue,
              unit = counter.getUnit,
              document = counter.getDocument,
              queryTime = counter.getQueryTime,
              startTime = Some(counter.getStartTime)
            )
          }.toList // convert to serializable collection
      }
    def streamAppToMeters(streamClusterInfo: StreamClusterInfo): Map[String, Seq[Meter]] =
      streamCollie.counters(streamClusterInfo).groupBy(_.group()).map {
        case (group, counters) =>
          group -> counters.map { counter =>
            Meter(
              value = counter.getValue,
              unit = counter.getUnit,
              document = counter.getDocument,
              queryTime = counter.getQueryTime,
              startTime = Some(counter.getStartTime)
            )
          }.toList // convert to serializable collection
      }
    MeterCache.builder
      .refresher(
        () =>
          // we do the sync here to simplify the interface
          Await.result(
            clusterCollie
              .clusters()
              .map(_.keys
                .map {
                  case brokerClusterInfo: BrokerClusterInfo => brokerClusterInfo -> brokerToMeters(brokerClusterInfo)
                  case workerClusterInfo: WorkerClusterInfo => workerClusterInfo -> workerToMeters(workerClusterInfo)
                  case streamClusterInfo: StreamClusterInfo => streamClusterInfo -> streamAppToMeters(streamClusterInfo)
                  case clusterInfo: ClusterInfo             => clusterInfo -> Map.empty[String, Seq[Meter]]
                }
                .toSeq
                .toMap),
            // TODO: how to set a suitable timeout ??? by chia
            cacheTimeout * 5
        ))
      .frequency(cacheTimeout)
      .build
  }

  private[this] implicit val adminCleaner: AdminCleaner = new AdminCleaner(cleanupTimeout)

  /**
    * the full route consists from all routes against all subclass from ohara data and a final route used to reject other requests.
    */
  private[this] def basicRoute(): server.Route = pathPrefix(ConfiguratorApiInfo.V0)(
    Seq[server.Route](
      TopicRoute.apply,
      HdfsInfoRoute.apply,
      FtpInfoRoute.apply,
      JdbcInfoRoute.apply,
      PipelineRoute.apply,
      ValidationRoute.apply,
      QueryRoute.apply,
      ConnectorRoute.apply,
      InfoRoute.apply(mode),
      StreamRoute.apply,
      ShabondiRoute.apply,
      NodeRoute.apply,
      ZookeeperRoute.apply,
      BrokerRoute.apply,
      WorkerRoute.apply,
      FileRoute.apply,
      // the route of downloading jar is moved to jar store so we have to mount it manually.
      fileStore.route,
      LogRoute.apply,
      ObjectRoute.apply,
      ContainerRoute.apply,
      DefinitionRoute.apply
    ).reduce[server.Route]((a, b) => a ~ b))

  private[this] def privateRoute(): server.Route =
    pathPrefix(ConfiguratorApiInfo.PRIVATE)(path(Remaining)(path =>
      complete(StatusCodes.NotFound -> s"you have to buy the license for advanced API: $path")))

  private[this] def finalRoute(): server.Route =
    path(Remaining)(routeToOfficialUrl)

  private[this] implicit val actorSystem: ActorSystem = ActorSystem(s"${classOf[Configurator].getSimpleName}-system")
  private[this] implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()
  private[this] val httpServer: Http.ServerBinding =
    try Await.result(
      Http().bindAndHandle(
        handler = handleExceptions(exceptionHandler())(
          handleRejections(rejectionHandler())(basicRoute() ~ privateRoute()) ~ finalRoute()),
        // we bind the service on all network adapter.
        interface = CommonUtils.anyLocalAddress(),
        port = port
      ),
      initializationTimeout.toMillis milliseconds
    )
    catch {
      case e: Throwable =>
        Releasable.close(this)
        throw e
    }

  /**
    * Do what you want to do when calling closing.
    */
  override protected def doClose(): Unit = {
    log.info("start to close Ohara Configurator")
    val start = CommonUtils.current()
    // close the cache thread in order to avoid cache error in log
    Releasable.close(meterCache)
    val onceHttpTerminated =
      if (httpServer != null)
        Some(httpServer.terminate(terminateTimeout).flatMap(_ => actorSystem.terminate()))
      else if (actorSystem == null) None
      else Some(actorSystem.terminate())
    // manually close materializer in order to speedup the release of http server and actor system
    if (actorMaterializer != null) actorMaterializer.shutdown()
    onceHttpTerminated.foreach { f =>
      try Await.result(f, terminateTimeout)
      catch {
        case e: Throwable =>
          log.error("failed to close http server and actor system", e)
      }
    }
    if (threadPool != null) {
      threadPool.shutdownNow()
      if (!threadPool.awaitTermination(terminateTimeout.toMillis, TimeUnit.MILLISECONDS))
        log.error("failed to terminate all running threads!!!")
    }
    Releasable.close(clusterCollie)
    Releasable.close(fileStore)
    Releasable.close(store)
    k8sClient.foreach(Releasable.close)
    Releasable.close(adminCleaner)
    log.info(s"succeed to close Ohara Configurator. elapsed:${CommonUtils.current() - start} ms")
  }
}

object Configurator {

  def builder: ConfiguratorBuilder = new ConfiguratorBuilder()

  private[configurator] val DATA_SERIALIZER: Serializer[Data] = new Serializer[Data] {
    override def to(obj: Data): Array[Byte] = Serializer.OBJECT.to(obj)
    override def from(bytes: Array[Byte]): Data =
      Serializer.OBJECT.from(bytes).asInstanceOf[Data]
  }

  //----------------[main]----------------//
  private[configurator] lazy val LOG = Logger(Configurator.getClass)
  private[configurator] val HELP_KEY = "--help"
  private[configurator] val FOLDER_KEY = "--folder"
  private[configurator] val HOSTNAME_KEY = "--hostname"
  private[configurator] val K8S_KEY = "--k8s"
  private[configurator] val FAKE_KEY = "--fake"
  private[configurator] val PORT_KEY = "--port"
  private val USAGE = s"[Usage] $FOLDER_KEY $HOSTNAME_KEY $PORT_KEY $K8S_KEY $FAKE_KEY"

  /**
    * parse input arguments and then generate a Configurator instance.
    * @param args input arguments
    * @return configurator instance
    */
  private[configurator] def configurator(args: Array[String]): Configurator = {
    val configuratorBuilder = Configurator.builder
    try {
      args.sliding(2, 2).foreach {
        case Array(FOLDER_KEY, value)   => configuratorBuilder.homeFolder(value)
        case Array(HOSTNAME_KEY, value) => configuratorBuilder.hostname(value)
        case Array(PORT_KEY, value)     => configuratorBuilder.port(value.toInt)
        case Array(K8S_KEY, value) =>
          configuratorBuilder.k8sClient(K8SClient(value))
        case Array(FAKE_KEY, value) =>
          if (value.toBoolean) configuratorBuilder.fake()
        case _ =>
          configuratorBuilder.cleanup()
          throw new IllegalArgumentException(s"input:${args.mkString(" ")}. $USAGE")
      }
      configuratorBuilder.build()
    } catch {
      case e: Throwable =>
        // release all pre-created objects
        configuratorBuilder.cleanup()
        throw e
    }
  }

  def main(args: Array[String]): Unit = {
    if (args.length == 1 && args(0) == HELP_KEY) {
      println(USAGE)
      return
    }
    if (GLOBAL_CONFIGURATOR != null) throw new RuntimeException("configurator is running!!!")

    GLOBAL_CONFIGURATOR = configurator(args)

    try {
      LOG.info(
        s"start a configurator built on hostname:${GLOBAL_CONFIGURATOR.hostname} and port:${GLOBAL_CONFIGURATOR.port}")
      LOG.info("enter ctrl+c to terminate the configurator")
      while (!GLOBAL_CONFIGURATOR_SHOULD_CLOSE) {
        TimeUnit.SECONDS.sleep(2)
        LOG.info(s"Current data size:${GLOBAL_CONFIGURATOR.size}")
      }
    } catch {
      case _: InterruptedException => LOG.info("prepare to die")
    } finally {
      Releasable.close(GLOBAL_CONFIGURATOR)
      GLOBAL_CONFIGURATOR = null
      HttpExecutor.close()
    }
  }

  /**
    * visible for testing.
    */
  @volatile private[configurator] var GLOBAL_CONFIGURATOR: Configurator = _

  /**
    * visible for testing.
    */
  private[configurator] def GLOBAL_CONFIGURATOR_RUNNING: Boolean = GLOBAL_CONFIGURATOR != null

  /**
    * visible for testing.
    */
  @volatile private[configurator] var GLOBAL_CONFIGURATOR_SHOULD_CLOSE = false

  abstract sealed class Mode

  /**
    * show the mode of running configurator.
    */
  object Mode extends com.island.ohara.client.Enum[Mode] {

    /**
      * No extra services are running. Configurator fake all content of response for all requests. This mode is useful to test only the APIs
      */
    case object FAKE extends Mode
    case object SSH extends Mode
    case object K8S extends Mode
  }
}
