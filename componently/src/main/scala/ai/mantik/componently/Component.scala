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
package ai.mantik.componently

import com.typesafe.scalalogging.Logger

import scala.concurrent.Future

/**
  * Base trait for component like objects. This are things which need akka
  * access, have a life time.
  */
trait Component {
  implicit protected def akkaRuntime: AkkaRuntime

  /** Typesafe logger. */
  protected def logger: Logger
}

/** Base class for Components. */
abstract class ComponentBase(implicit protected val akkaRuntime: AkkaRuntime) extends Component with AkkaHelper {

  /** Typesafe logger. */
  protected final val logger: Logger = Logger(getClass)
  logger.trace("Initializing...")

  protected def addShutdownHook(f: => Future[_]): Unit = {
    akkaRuntime.lifecycle.addShutdownHook {
      logger.trace("Shutdown")
      f
    }
  }
}
