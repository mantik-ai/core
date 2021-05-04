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

import scala.reflect.ClassTag

/**
  * Adds a discriminator for encoding multiple algebraic sub types into the same json.
  * Similar to https://circe.github.io/circe/codecs/adt.html
  * but we can choose the discriminator and values by our self, to make JSON spec simpler.
  *
  * This is for circe JSON, not Play JSON.
  */
abstract class DiscriminatorDependentCodec[T](discriminator: String = "kind") extends ObjectEncoder[T] with Decoder[T] {
  protected case class SubType[X <: T](
      classTag: ClassTag[X],
      encoder: ObjectEncoder[X],
      decoder: Decoder[X],
      kind: String,
      isDefault: Boolean
  ) {
    final type subType = X

    def rawEncode(x: T): JsonObject = encoder.encodeObject(x.asInstanceOf[X])
  }

  /** Make a sub type with automatically derived encoders/decoders. */
  protected def makeSubType[X <: T](
      kind: String,
      isDefault: Boolean = false
  )(
      implicit classTag: ClassTag[X],
      encoder: Lazy[DerivedObjectEncoder[X]],
      decoder: Lazy[DerivedDecoder[X]]
  ): SubType[X] = {
    SubType(classTag, encoder.value, decoder.value, kind, isDefault)
  }

  /** Make a sub type with given encoders/decoders. */
  protected def makeGivenSubType[X <: T](
      kind: String,
      isDefault: Boolean = false
  )(implicit classTag: ClassTag[X], encoder: ObjectEncoder[X], decoder: Decoder[X]): SubType[X] = {
    SubType(classTag, encoder, decoder, kind, isDefault)
  }

  val subTypes: Seq[SubType[_ <: T]]

  private lazy val defaultSubType: Option[SubType[_ <: T]] = subTypes
    .filter(_.isDefault)
    .ensuring(_.size <= 1, "Only one default type required")
    .headOption

  private lazy val decoders: Map[String, SubType[_ <: T]] = subTypes
    .groupBy(_.kind)
    .mapValues(_.ensuring(_.size == 1, "Duplicate Detected").head)

  private lazy val classTags: Map[Class[_], SubType[_ <: T]] = subTypes
    .groupBy(_.classTag.runtimeClass)
    .mapValues(_.ensuring(_.size == 1, "Duplicate Class Detected").head)

  override def encodeObject(a: T): JsonObject = {
    classTags.get(a.getClass) match {
      case None => throw new IllegalArgumentException(s"Object ${a.getClass.getSimpleName} is not registered")
      case Some(subType) =>
        subType
          .rawEncode(a)
          .+:(
            discriminator,
            Json.fromString(subType.kind)
          )
    }
  }

  implicit def encoder: ObjectEncoder[T] = this

  override def apply(c: HCursor): Result[T] = {
    val discriminatorField = c.downField(discriminator)
    discriminatorField.as[String].toOption match {
      case None =>
        defaultSubType match {
          case Some(givenDefaultSubType) =>
            givenDefaultSubType.decoder.apply(c)
          case None =>
            Left(DecodingFailure(s"No kind given and no default type", Nil))
        }
      case Some(kind) =>
        decoders.get(kind) match {
          case Some(subType) =>
            subType.decoder.apply(c)
          case None =>
            Left(DecodingFailure(s"Unknown kind ${kind}", Nil))
        }
    }
  }

  implicit def decoder: Decoder[T] = this
}
