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
  * A (hierarchical) error code.
  *
  * In contrast to exceptions they can be transferred via gRpc and HTTP.
  *
  * @param code error code. Sub codes are handled by adding slashes.
  */
class ErrorCode(
    val code: String,
    val grpcCode: Code = Code.UNKNOWN
) {

  /** The code cut as path. */
  lazy val codePath = code.split('/').toIndexedSeq.filter(_.nonEmpty)

  override def equals(obj: Any): Boolean = {
    obj match {
      case e: ErrorCode if code == e.code => true
      case _                              => false
    }
  }

  /** Throw this error code as exception. */
  def throwIt(msg: String, cause: Throwable = null): Nothing = {
    throw toException(msg, cause)
  }

  /** Convert this error code into a exception. */
  def toException(msg: String, cause: Throwable = null): MantikException = {
    new MantikException(this, code + ": " + msg, cause)
  }

  override def hashCode(): Int = {
    code.hashCode
  }

  override def toString: String = {
    s"ErrorCode(${code})"
  }

  def isA(errorCode: ErrorCode): Boolean = {
    codePath.startsWith(errorCode.codePath)
  }

  /** Derive a new sub code taking over existing fields. */
  def derive(
      subCode: String,
      grpcCode: Option[Code] = None
  ): ErrorCode = {
    val fullCode = if (code == "") {
      subCode
    } else {
      code + "/" + subCode
    }
    new ErrorCode(
      fullCode,
      grpcCode.getOrElse(this.grpcCode)
    )
  }
}
