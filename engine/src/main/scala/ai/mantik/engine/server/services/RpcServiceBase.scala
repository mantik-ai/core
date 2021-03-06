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
package ai.mantik.engine.server.services

import ai.mantik.componently.ComponentBase
import ai.mantik.componently.rpc.RpcConversions
import ai.mantik.elements.errors.{InvalidMantikHeaderException, MantikException}
import com.typesafe.scalalogging.Logger
import io.grpc.Status.Code

import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.util.control.NonFatal

/** Common base trait for gRpc service implementations talking to Mantik Services. */
private[services] trait RpcServiceBase {
  self: ComponentBase =>

  /**
    * Handle errors. gRpc likes to just forwards them without any backtrace.
    * However we want them to be logged and perhaps translated.
    */
  protected def handleErrors[T](f: => Future[T]): Future[T] = {
    val result =
      try {
        f
      } catch {
        case NonFatal(e) =>
          // this is bad, the error happened while creating the future
          logger.error("Unhandled error, not in future", e)
          throw e
      }
    result.transform {
      case Success(value) => Success(value)
      case Failure(NonFatal(e)) if translateError.isDefinedAt(e) =>
        logger.debug("Something failed", e)
        Failure(translateError(e))
      case Failure(e) =>
        logger.warn("Something failed", e)
        Failure(e)
    }
  }

  /** The place to add more error handlers. */
  protected val translateError: PartialFunction[Throwable, Throwable] = { case e: MantikException =>
    e.toGrpc
  }

  /** Encode the error if there is a translation. */
  protected def encodeErrorIfPossible(e: Throwable): Throwable = {
    if (translateError.isDefinedAt(e)) {
      translateError(e)
    } else {
      e
    }
  }
}
