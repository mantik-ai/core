package ai.mantik.ds

import ai.mantik.ds.Errors.TypeNotFoundException
import ai.mantik.ds.helper.circe.{ CirceJson, DiscriminatorDependentCodec, EnumDiscriminatorCodec, ExtraCirceCodecs }
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

  def fundamentalTypeFromName(name: String): FundamentalType = {
    FundamentalTypeCodec.stringToElement(name).getOrElse {
      throw new TypeNotFoundException(s"Type ${name} not found")
    }
  }

  def fundamentalTypeToName(fundamentalType: FundamentalType): String = {
    FundamentalTypeCodec.elementToString(fundamentalType)
  }

  private implicit object tabularFormat extends ObjectEncoder[TabularData] with Decoder[TabularData] {

    override def encodeObject(a: TabularData): JsonObject = {
      val allFields = Seq(
        Some("columns" -> a.columns.asJson),
        a.rowCount.map(rc => "rowCount" -> Json.fromLong(rc))
      ).flatten
      JsonObject.fromIterable(
        allFields
      )
    }

    override def apply(c: HCursor): Result[TabularData] = {
      for {
        columns <- c.downField("columns").as[ListMap[String, DataType]]
        rowCount <- c.downField("rowCount").as[Option[Long]]
      } yield {
        TabularData(columns, rowCount)
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

  implicit private object imageComponentCodec extends ObjectEncoder[ImageComponent] with Decoder[ImageComponent] {
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

  private implicit object imageCodec extends ObjectEncoder[Image] with Decoder[Image] {

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

  implicit val tensorCodec = CirceJson.makeSimpleCodec[Tensor]

  private val complexTypeCodec = new DiscriminatorDependentCodec[DataType]("type") {
    override val subTypes = Seq(
      makeGivenSubType[TabularData]("tabular", true),
      makeGivenSubType[Image]("image"),
      makeGivenSubType[Tensor]("tensor")
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
