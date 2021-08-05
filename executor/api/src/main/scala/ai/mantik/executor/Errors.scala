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

import io.circe.Decoder.Result
import io.circe._

/** Errors from Executor. */
object Errors {

  class ExecutorException(val msg: String, val statusCode: Int, cause: Throwable = null)
      extends RuntimeException(msg, cause)

  implicit val encoder: ObjectEncoder[ExecutorException] = new ObjectEncoder[ExecutorException] {

    override def encodeObject(a: ExecutorException): JsonObject = JsonObject(
      "code" -> Json.fromInt(a.statusCode),
      "error" -> Json.fromString(a.msg)
    )
  }

  implicit val decoder: Decoder[ExecutorException] = new Decoder[ExecutorException] {
    override def apply(c: HCursor): Result[ExecutorException] = {
      for {
        code <- c.downField("code").as[Int]
        msg <- c.downField("error").as[String]
      } yield {
        code match {
          case 400 => new BadRequestException(msg)
          case 404 => new NotFoundException(msg)
          case 409 => new ConflictException(msg)
          case 500 => new InternalException(msg)
          case 502 => new CouldNotExecutePayload(msg)
          case _   => new ExecutorException(msg, code)
        }
      }
    }
  }

  /** A resource was not found. */
  class NotFoundException(msg: String, cause: Throwable = null) extends ExecutorException(msg, 404, cause)

  /** A Conflict (e.g. Exists Already). */
  class ConflictException(msg: String) extends ExecutorException(msg, 409)

  /** The request was not ok */
  class BadRequestException(msg: String) extends ExecutorException(msg, 400)

  /** A strange internal error. */
  class InternalException(msg: String, cause: Throwable = null) extends ExecutorException(msg, 500, cause)

  /** There was a problem with the payload. */
  class CouldNotExecutePayload(msg: String) extends ExecutorException(msg, 502)
}
