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
package ai.mantik.engine.session

import ai.mantik.elements.errors.ErrorCode
import io.grpc.Status.Code

object EngineErrors {

  /** There was an engine specific error. */
  val EngineError = new ErrorCode("Engine")

  /** The session was not found. */
  val SessionNotFound = EngineError.derive("SessionNotFound", grpcCode = Some(Code.NOT_FOUND))

  /** The item you wanted to combine has not the reqired type. */
  val ItemUnexpectedType = EngineError.derive("ItemUnexpectedType", grpcCode = Some(Code.INVALID_ARGUMENT))

  /** The item was not found in the session. */
  val ItemNotFoundInSession = EngineError.derive("ItemNotFoundInSession", grpcCode = Some(Code.NOT_FOUND))
}
