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
package ai.mantik.elements.errors

import io.grpc.Status.Code

/**
  * Main Error codes used for [[MantikException]].
  * They provide enough information for serializing/deserializing common error cases.
  */
object ErrorCodes {
  private val defaultCodeBuilder = Seq.newBuilder[ErrorCode]

  private def add(errorCode: ErrorCode): ErrorCode = {
    defaultCodeBuilder += errorCode
    errorCode
  }

  private def addCode(code: String, grpcCode: Code): ErrorCode = {
    val c = RootCode.derive(code, Some(grpcCode))
    defaultCodeBuilder += c
    c
  }

  val RootCode = new ErrorCode("", Code.UNKNOWN)

  val MantikItem = addCode("MantikItem", Code.INVALID_ARGUMENT)

  val MantikItemNotFound = add(MantikItem.derive("NotFound", Some(Code.NOT_FOUND)))

  val MantikItemPayloadNotFound = add(MantikItem.derive("PayloadNotFound", Some(Code.NOT_FOUND)))

  val MantikItemWrongType = add(MantikItem.derive("WrongType", Some(Code.INVALID_ARGUMENT)))

  val MantikItemConflict = add(MantikItem.derive("Conflict", Some(Code.FAILED_PRECONDITION)))

  val MantikItemInvalidBridge = add(MantikItem.derive("InvalidBridge", Some(Code.FAILED_PRECONDITION)))

  val InvalidMantikHeader = addCode("InvalidMantikHeader", Code.INVALID_ARGUMENT)

  val InvalidMantikId = addCode("InvalidMantikId", Code.INVALID_ARGUMENT)

  val Configuration = addCode("Configuration", Code.INTERNAL)

  val LocalRegistry = addCode("LocalRegistry", Code.INTERNAL)

  val RemoteRegistryFailure = addCode("RemoteRegistry", Code.UNKNOWN)

  val RemoteRegistryCouldNotGetToken = add(RemoteRegistryFailure.derive("CouldNotGetToken"))

  val ProtocolError = addCode("ProtocolError", Code.INVALID_ARGUMENT)

  val InternalError = addCode("Internal", Code.INTERNAL)

  /** Return a list of default error codes. */
  lazy val defaultErrorCodes: Seq[ErrorCode] = defaultCodeBuilder.result()
}
