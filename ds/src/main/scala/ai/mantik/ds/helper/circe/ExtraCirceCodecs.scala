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

import scala.collection.immutable.ListMap

/** Extra implicit codecs for Circe JSON. */
object ExtraCirceCodecs {

  implicit def enumMapDecoder[K, V](
      implicit subDecoder: Decoder[ListMap[String, V]],
      ed: EnumDiscriminatorCodec[K]
  ): Decoder[ListMap[K, V]] = new Decoder[ListMap[K, V]] {
    override def apply(c: HCursor): Result[ListMap[K, V]] = {
      for {
        plainMapDecoded <- subDecoder(c)
        keysDecoded <- decodeKeys(plainMapDecoded.keys)
      } yield {

        ListMap(
          keysDecoded.zip(plainMapDecoded.values): _*
        )
      }
    }

    private def decodeKeys(iterable: Iterable[String]): Result[Seq[K]] = {
      val resultBuilder = Seq.newBuilder[K]
      val it = iterable.iterator
      while (it.hasNext) {
        decodeKey(it.next()) match {
          case Left(err) => return Left(err)
          case Right(x)  => resultBuilder += x
        }
      }
      Right(resultBuilder.result())
    }

    private def decodeKey(s: String): Result[K] = {
      ed.stringToElement(s) match {
        case None    => Left(DecodingFailure(s"Unknown key ${s}", Nil))
        case Some(x) => Right(x)
      }
    }
  }

  implicit def enumMapEncoder[K, V](
      implicit subDecoder: Encoder[V],
      ed: EnumDiscriminatorCodec[K]
  ): ObjectEncoder[ListMap[K, V]] = new ObjectEncoder[ListMap[K, V]] {
    override def encodeObject(a: ListMap[K, V]): JsonObject = {
      JsonObject.fromIterable(
        a.map { case (k, v) => ed.elementToString(k) -> subDecoder(v) }
      )
    }
  }

}
