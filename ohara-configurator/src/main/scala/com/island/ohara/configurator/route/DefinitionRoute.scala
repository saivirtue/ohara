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

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.server
import akka.http.scaladsl.server.Directives._
import com.island.ohara.agent.{ClusterCollie, NodeCollie}
import com.island.ohara.client.configurator.v0.DefinitionApi._

import scala.concurrent.ExecutionContext
private[configurator] object DefinitionRoute {

  def apply(implicit clusterCollie: ClusterCollie,
            nodeCollie: NodeCollie,
            executionContext: ExecutionContext): server.Route =
    pathPrefix(DEFINITION_PREFIX_PATH) {
      path(Segment) { name =>
        entity(as[Params]) { params =>
          put {
            complete(ServiceType.forName(name) match {
              case ServiceType.Zookeeper => clusterCollie.zookeeperCollie.fetchDefinitions(params)
              case ServiceType.Broker    => clusterCollie.brokerCollie.fetchDefinitions(params)
              case ServiceType.Worker    => clusterCollie.workerCollie.fetchDefinitions(params)
              case ServiceType.Stream    => clusterCollie.streamCollie.fetchDefinitions(params)
              case ServiceType.Topic     => ???
              case other                 => throw new RuntimeException(s"We cannot fetch the definition for [$other] type")
            })
          }
        }
      }
    }
}
