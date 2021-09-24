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
import io.circe.generic.encoding.{DerivedAsObjectEncoder, DerivedObjectEncoder}
import shapeless.Lazy

import scala.reflect.ClassTag

/**
  * A JSON codec which is built from other codecs and just tries them out during decoding.
  * Similar to [[DiscriminatorDependentCodec]] but without the kind type as they are all distinct.
  */
abstract class TrialDependentCodec[T] extends Encoder.AsObject[T] with Decoder[T] {
  protected case class SubType[X <: T](
      classTag: ClassTag[X],
      encoder: Encoder.AsObject[X],
      decoder: Decoder[X]
  ) {
    final type subType = X

    def rawEncode(x: T): JsonObject = encoder.encodeObject(x.asInstanceOf[X])
  }

  /** Make a sub type with automatically derived encoders/decoders. */
  protected def makeSubType[X <: T]()(
      implicit classTag: ClassTag[X],
      encoder: Lazy[DerivedAsObjectEncoder[X]],
      decoder: Lazy[DerivedDecoder[X]]
  ): SubType[X] = {
    SubType(classTag, encoder.value, decoder.value)
  }

  /** Make a sub type with given encoders/decoders. */
  protected def makeGivenSubType[X <: T]()(
      implicit classTag: ClassTag[X],
      encoder: Encoder.AsObject[X],
      decoder: Decoder[X]
  ): SubType[X] = {
    SubType(classTag, encoder, decoder)
  }

  val subTypes: Seq[SubType[_ <: T]]

  private lazy val classTags: Map[Class[_], SubType[_ <: T]] = subTypes
    .groupBy(_.classTag.runtimeClass)
    .view
    .mapValues(_.ensuring(_.size == 1, "Duplicate Class Detected").head)
    .toMap

  override def encodeObject(a: T): JsonObject = {
    classTags.get(a.getClass) match {
      case None          => throw new IllegalArgumentException(s"Object ${a.getClass.getSimpleName} is not registered")
      case Some(subType) => subType.rawEncode(a)
    }
  }

  override def apply(c: HCursor): Result[T] = {
    val it = subTypes.iterator
    while (it.hasNext) {
      val decoder = it.next()
      decoder.decoder(c) match {
        case Right(value) => return Right(value)
        case Left(_)      => // try next
      }
    }
    Left(DecodingFailure(s"No matching decoder found", c.history))
  }
}
