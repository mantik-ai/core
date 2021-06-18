/*
 * This file is part of the Mantik Project.
 * Copyright (c) 2020-2021 Mantik UG (Haftungsbeschränkt)
 * Authors: See AUTHORS file
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.
 *
 * Additionally, the following linking exception is granted:
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with other code, such other code is not for that reason
 * alone subject to any of the requirements of the GNU Affero GPL
 * version 3.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license.
 */
package ai.mantik.engine

import ai.mantik.componently.AkkaRuntime
import ai.mantik.componently.di.AkkaModule
import ai.mantik.engine.buildinfo.BuildInfo
import ai.mantik.engine.server.{EngineServer, ServiceModule}
import ai.mantik.ui.server.UiServer
import com.google.inject.Guice
import org.slf4j.LoggerFactory
import org.slf4j.bridge.SLF4JBridgeHandler

object Main {
  private val logger = LoggerFactory.getLogger(getClass)

  // forwarding j.u.l.Logging to SLF4J
  SLF4JBridgeHandler.removeHandlersForRootLogger()
  SLF4JBridgeHandler.install()

  def main(args: Array[String]): Unit = {
    logger.info(s"Initializing Mantik Engine ${BuildInfo}")
    implicit val akkaRuntime = AkkaRuntime.createNew()

    try {
      val injector = Guice.createInjector(
        new AkkaModule(),
        new EngineModule(),
        ServiceModule
      )

      val server = injector.getInstance(classOf[EngineServer])
      server.start()

      val ui = injector.getInstance(classOf[UiServer])

      server.waitUntilFinished()
    } catch {
      case e: Exception =>
        logger.error("Error ", e)
        System.exit(1)
    } finally {
      System.exit(0)
    }

  }
}
