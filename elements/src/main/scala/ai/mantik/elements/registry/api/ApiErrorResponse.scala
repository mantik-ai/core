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
package ai.mantik.elements.registry.api

import io.circe.generic.JsonCodec

@JsonCodec
case class ApiErrorResponse(
    code: String,
    message: Option[String]
)

/** Contains error codes which are not part of [[ai.mantik.elements.errors.ErrorCodes]] */
object ApiErrorResponse {

  // Error codes

  /** There was a Bad Request. */
  val BadRequest = "BadRequest"

  /** Some resource not found. */
  val NotFound = "NotFound"

  /** There was no permission to access this element */
  val NoPermission = "NoPermission"

  val InvalidMantikHeader = "InvalidMantikHeader"

  val InvalidMantikId = "InvalidMantikId"

  /** The login was invalid */
  val InvalidLogin = "InvalidLogin"

  /** The token was invalid. */
  val InvalidToken = "InvalidToken"

}
