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
package ai.mantik.ds.helper.circe

import io.circe.Decoder.Result
import io.circe._
import io.circe.generic.decoding.DerivedDecoder
import io.circe.generic.encoding.DerivedObjectEncoder
import shapeless.Lazy

/** Helper for Circe Json. */
object CirceJson {

  def forceParseJson(s: String): Json = {
    parser.parse(s) match {
      case Right(ok)     => ok
      case Left(failure) => throw failure
    }
  }

  /** Strip all elements in in JSON objects whose value is null. */
  def stripNullValues(json: Json): Json = {
    json.arrayOrObject(
      json,
      jsonArray => {
        Json.arr(
          jsonArray.map(stripNullValues): _*
        )
      },
      jsonObject => {
        Json.fromJsonObject(stripNullValues(jsonObject))
      }
    )
  }

  /** Strip all elements in in JSON objects whose value is null. */
  def stripNullValues(json: JsonObject): JsonObject = {
    json.filter(!_._2.isNull).mapValues(stripNullValues)
  }

  /** Auto generates a Encoder/Decoder for Circe JSON. */
  def makeSimpleCodec[T](
      implicit encoder: Lazy[DerivedObjectEncoder[T]],
      decoder: Lazy[DerivedDecoder[T]]
  ): ObjectEncoder[T] with Decoder[T] = {
    val encoderImpl = encoder.value
    val decoderImpl = decoder.value
    new ObjectEncoder[T] with Decoder[T] {
      override def encodeObject(a: T): JsonObject = encoderImpl.encodeObject(a)

      override def apply(c: HCursor): Result[T] = decoderImpl.apply(c)
    }
  }
}
