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
package ai.mantik.ds

object Errors {

  /** A type with given name was not found. */
  class TypeNotFoundException(msg: String) extends RuntimeException(msg)

  /** A format like this is not supported. */
  class FormatNotSupportedException(msg: String) extends RuntimeException(msg)

  /** A Format definition is in a way not logical. */
  class FormatDefinitionException(msg: String) extends RuntimeException(msg)

  /** Something is not encoded in the way we expect it. */
  class EncodingException(msg: String, cause: Throwable = null) extends RuntimeException(msg, cause)

  /** If some feature is not supported. */
  class FeatureNotSupported(msg: String, cause: Throwable = null) extends RuntimeException(msg, cause)
}
