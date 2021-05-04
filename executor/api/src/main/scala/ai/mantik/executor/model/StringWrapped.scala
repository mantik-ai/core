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
package ai.mantik.executor.model

import io.circe.{Decoder, Encoder, Json}

import scala.language.implicitConversions

/**
  * Wraps some plain type in a way that it's serialized as a string.
  * Used for encoding values in Strings (e.g. Query Parameters)
  */
case class StringWrapped[T](
    value: T
)

object StringWrapped {
  implicit def fromValue[T](value: T): StringWrapped[T] = {
    StringWrapped(value)
  }

  implicit def encode[T](implicit e: Encoder[T]): Encoder[StringWrapped[T]] = {
    Encoder { v =>
      Json.fromString(e(v.value).toString())
    }
  }

  implicit def decode[T](implicit e: Decoder[T]): Decoder[StringWrapped[T]] = {
    Decoder.decodeString.emap { string =>
      for {
        json <- io.circe.parser.parse(string).left.map(_.toString)
        value <- e.decodeJson(json).left.map(_.toString)
      } yield {
        StringWrapped(value)
      }
    }
  }
}
