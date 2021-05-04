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
package ai.mantik.testutils

import org.scalatest.{Assertions, EitherValues}

/**
  * Adds helpers for unpacking Either.
  * Like [[EitherValues]] but with more debug output.
  */
trait EitherExt {
  self: Assertions =>

  class EitherExt[L, R](in: Either[L, R]) {

    def forceRight: R = {
      in match {
        case Left(value) =>
          value match {
            case t: Throwable =>
              fail(s"Got left, wanted right on try: ${t.getMessage}", t)
            case other =>
              fail(s"Got left, wanted right on try ${other}")
          }
        case Right(value) =>
          value
      }
    }

    def forceLeft: L = {
      in match {
        case Right(value) =>
          fail(s"Got right, wanted right on try value=${value}")
        case Left(value) =>
          value
      }
    }
  }

  import scala.language.implicitConversions
  implicit def toEitherExt[A, B](in: Either[A, B]): EitherExt[A, B] = new EitherExt[A, B](in)

}
