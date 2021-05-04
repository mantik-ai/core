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
package ai.mantik.componently.utils

/** Extend Either. */
object EitherExtensions {
  import scala.language.implicitConversions

  /** Extensions for Either where the left value is something throwable. */
  class ThrowableEitherExt[L <: Throwable, R](in: Either[L, R]) {
    def force: R = {
      in match {
        case Left(value)  => throw value
        case Right(value) => value
      }
    }
  }

  /** Extension for Either where the left value is an error string. */
  class StringErrorEither[R](in: Either[String, R]) {
    def force: R = {
      in match {
        case Left(error)  => throw new NoSuchElementException(s"Got left with error message ${error}")
        case Right(value) => value
      }
    }
  }

  implicit def toThrowableEither[L <: Throwable, R](in: Either[L, R]): ThrowableEitherExt[L, R] =
    new ThrowableEitherExt[L, R](in)

  implicit def toStringErrorEither[R](in: Either[String, R]): StringErrorEither[R] = new StringErrorEither[R](in)
}
