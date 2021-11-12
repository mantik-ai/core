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
package ai.mantik.executor

/** Errors from Executor. */
object Errors {

  /** Base class for errors from the executor. */
  class ExecutorException(val msg: String, cause: Throwable = null) extends RuntimeException(msg, cause)

  /** A resource was not found. */
  class NotFoundException(msg: String, cause: Throwable = null) extends ExecutorException(msg, cause)

  /** A Conflict (e.g. Exists Already). */
  class ConflictException(msg: String) extends ExecutorException(msg)

  /** The request was not ok */
  class BadRequestException(msg: String) extends ExecutorException(msg)

  /** A strange internal error. */
  class InternalException(msg: String, cause: Throwable = null) extends ExecutorException(msg, cause)

  /** There was a problem with the payload. */
  class CouldNotExecutePayload(msg: String) extends ExecutorException(msg)
}
