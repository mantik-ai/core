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

/** Invalid MantikHeader. */
class InvalidMantikHeaderException(msg: String, cause: Throwable = null)
    extends MantikException(ErrorCodes.InvalidMantikHeader, msg, cause)

object InvalidMantikHeaderException {

  /** Wrap an exception into an InvalidMantikHeaderException. */
  def wrap(e: Throwable): InvalidMantikHeaderException = {
    e match {
      case a: InvalidMantikHeaderException => a
      case other                           => new InvalidMantikHeaderException(Option(e.getMessage).getOrElse(other.getClass.getSimpleName), e)
    }
  }
}
