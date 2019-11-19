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

package com.island.ohara.shabondi

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Route
import com.island.ohara.shabondi.Model.{HttpSink, HttpSource, _}

private class WebServer(private val serverType: ServerType) extends AbstractWebServer with SourceRoute with SinkRoute {
  private val _system = ActorSystem("shabondi")

  override implicit def actorSystem: ActorSystem = _system

  override protected def routes: Route = serverType match {
    case HttpSource => sourceRoute
    case HttpSink   => sinkRoute
  }
}
