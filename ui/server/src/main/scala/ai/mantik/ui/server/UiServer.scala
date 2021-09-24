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
package ai.mantik.ui.server

import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.ui.StateService
import akka.http.scaladsl.Http

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

/** Small embedded server which renders the current state of Mantik */
@Singleton
class UiServer(config: UiConfig, stateService: StateService)(implicit akkaRuntime: AkkaRuntime) extends ComponentBase {
  @Inject
  def this(stateService: StateService)(implicit akkaRuntime: AkkaRuntime) = {
    this(UiConfig.fromTypesafe(akkaRuntime.config), stateService)
  }

  private val resourceClassLoader = getClass.getClassLoader
  private lazy val uiRouter = new UiRouter(resourceClassLoader, stateService)

  val maybeBindResult: Option[Future[Http.ServerBinding]] = if (!config.enabled) {
    logger.info(s"UiServer not enabled")
    None
  } else {
    logger.info(s"Starting UiServer on ${config.interface}:${config.port}")
    val bindResult = Http().newServerAt(config.interface, config.port).bind(uiRouter.route)
    if (config.port == 0) {
      bindResult.foreach { result =>
        logger.info(s"Successfully bound on port ${result.localAddress.getPort}")
      }
    }
    Some(bindResult)
  }

  def boundPort: Future[Int] = {
    maybeBindResult match {
      case None         => Future.failed(new IllegalStateException(s"Not active"))
      case Some(result) => result.map(_.localAddress.getPort)
    }
  }

  addShutdownHook {
    maybeBindResult match {
      case None => Future.successful(())
      case Some(bind) =>
        bind.flatMap { serverBinding =>
          serverBinding.unbind()
        }
    }
  }
}
