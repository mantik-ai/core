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

import ai.mantik.ds.Errors.TypeNotFoundException
import ai.mantik.ds.helper.circe.{CirceJson, DiscriminatorDependentCodec, EnumDiscriminatorCodec, ExtraCirceCodecs}
import io.circe.Decoder.Result
import io.circe._
import io.circe.syntax._

import scala.collection.immutable.ListMap

/**
  * Serialization format for data types
  *
  * Principle:
  * - Strings are reserved for fundamental types
  * - Objects without 'type' are reserved for tabular types
  * - all other have a 'type' flag
  */
private[ds] object DataTypeJsonAdapter {

  implicit val FundamentalTypeCodec = new EnumDiscriminatorCodec[FundamentalType](
    Seq(
      "int8" -> FundamentalType.Int8,
      "int32" -> FundamentalType.Int32,
      "int64" -> FundamentalType.Int64,
      "uint8" -> FundamentalType.Uint8,
      "uint32" -> FundamentalType.Uint32,
      "uint64" -> FundamentalType.Uint64,
      "bool" -> FundamentalType.BoolType,
      "string" -> FundamentalType.StringType,
      "float32" -> FundamentalType.Float32,
      "float64" -> FundamentalType.Float64,
      "void" -> FundamentalType.VoidType
    )
  )

  def fundamentalTypeFromName(name: String): Option[FundamentalType] = {
    FundamentalTypeCodec.stringToElement(name.toLowerCase)
  }

  def fundamentalTypeToName(fundamentalType: FundamentalType): String = {
    FundamentalTypeCodec.elementToString(fundamentalType)
  }

  private implicit object tabularFormat extends Encoder.AsObject[TabularData] with Decoder[TabularData] {

    override def encodeObject(a: TabularData): JsonObject = {
      val allFields = Seq(
        Some("columns" -> a.columns.asJson)
      ).flatten
      JsonObject.fromIterable(
        allFields
      )
    }

    override def apply(c: HCursor): Result[TabularData] = {
      for {
        columns <- c.downField("columns").as[ListMap[String, DataType]]
      } yield {
        TabularData(columns)
      }
    }
  }

  implicit private val channelCodec = new EnumDiscriminatorCodec[ImageChannel](
    Seq(
      "red" -> ImageChannel.Red,
      "green" -> ImageChannel.Green,
      "blue" -> ImageChannel.Blue,
      "black" -> ImageChannel.Black
    )
  )

  def imageChannelName(imageChannel: ImageChannel): String = {
    channelCodec.elementToString(imageChannel)
  }

  def imageChannelFromName(name: String): Option[ImageChannel] = {
    channelCodec.stringToElement(name.toLowerCase)
  }

  implicit private object imageComponentCodec extends Encoder.AsObject[ImageComponent] with Decoder[ImageComponent] {
    override def encodeObject(a: ImageComponent): JsonObject = {
      JsonObject(
        "componentType" -> a.componentType.asJson(FundamentalTypeCodec)
      )
    }

    override def apply(c: HCursor): Result[ImageComponent] = {
      for {
        componentType <- c.downField("componentType").as[FundamentalType](FundamentalTypeCodec)
      } yield ImageComponent(componentType)
    }
  }

  implicit private val imageFormatCodec = new EnumDiscriminatorCodec[ImageFormat](
    Seq(
      "plain" -> ImageFormat.Plain,
      "png" -> ImageFormat.Png
    )
  )

  private implicit object imageCodec extends Encoder.AsObject[Image] with Decoder[Image] {

    implicit val componentsDecoder = ExtraCirceCodecs.enumMapDecoder[ImageChannel, ImageComponent]
    implicit val componentsEncoder = ExtraCirceCodecs.enumMapEncoder[ImageChannel, ImageComponent]

    override def encodeObject(a: Image): JsonObject = {
      JsonObject(
        "width" -> a.width.asJson,
        "height" -> a.height.asJson,
        "components" -> a.components.asJson,
        "format" -> a.format.asJson
      )
    }

    override def apply(c: HCursor): Result[Image] = {
      for {
        width <- c.downField("width").as[Int]
        height <- c.downField("height").as[Int]
        components <- c.downField("components").as[ListMap[ImageChannel, ImageComponent]]
        format <- c.downField("format").as[Option[ImageFormat]].map(_.getOrElse(ImageFormat.Plain))
      } yield Image(width, height, components, format)
    }
  }

  private implicit val tensorCodec = CirceJson.makeSimpleCodec[Tensor]

  private implicit object nullableCodec extends Encoder.AsObject[Nullable] with Decoder[Nullable] {
    override def encodeObject(a: Nullable): JsonObject = {
      JsonObject(
        "underlying" -> typeEncoder(a.`underlying`)
      )
    }

    override def apply(c: HCursor): Result[Nullable] = {
      for {
        subType <- c.downField("underlying").as[DataType]
      } yield Nullable(subType)
    }
  }

  private implicit object arrayCodec extends Encoder.AsObject[ArrayT] with Decoder[ArrayT] {
    override def encodeObject(a: ArrayT): JsonObject = {
      JsonObject(
        "underlying" -> typeEncoder(a.underlying)
      )
    }

    override def apply(c: HCursor): Result[ArrayT] = {
      for {
        underlying <- c.downField("underlying").as[DataType]
      } yield ArrayT(underlying)
    }
  }

  private implicit object structCodec extends Encoder.AsObject[Struct] with Decoder[Struct] {
    override def encodeObject(a: Struct): JsonObject = {
      JsonObject(
        "fields" -> a.fields.asJson
      )
    }

    override def apply(c: HCursor): Result[Struct] = {
      for {
        fields <- c.downField("fields").as[ListMap[String, DataType]]
      } yield {
        Struct(fields)
      }
    }
  }

  private val complexTypeCodec = new DiscriminatorDependentCodec[DataType]("type") {
    override val subTypes = Seq(
      makeGivenSubType[TabularData]("tabular", true),
      makeGivenSubType[Image]("image"),
      makeGivenSubType[Tensor]("tensor"),
      makeGivenSubType[Nullable]("nullable"),
      makeGivenSubType[ArrayT]("array"),
      makeGivenSubType[Struct]("struct")
    )
  }

  /** Combines all into one encoder. */
  implicit lazy val typeEncoder: Encoder[DataType] = new Encoder[DataType] {
    override def apply(a: DataType): Json = {
      a match {
        case f: FundamentalType => FundamentalTypeCodec.apply(f)
        case o                  => complexTypeCodec.encoder(o)
      }
    }
  }

  implicit lazy val typeDecoder: Decoder[DataType] = new Decoder[DataType] {
    override def apply(c: HCursor): Result[DataType] = {
      if (c.value.isString) {
        FundamentalTypeCodec(c)
      } else {
        complexTypeCodec.decoder(c)
      }
    }
  }
}
