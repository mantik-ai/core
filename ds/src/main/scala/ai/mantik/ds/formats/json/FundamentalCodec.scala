package ai.mantik.ds.formats.json

import ai.mantik.ds.FundamentalType
import ai.mantik.ds.element.{Element, Primitive, PrimitiveEncoder}
import io.circe.Decoder.Result
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json}
import io.circe.syntax._

/** Holds Codecs for Fundamental Data Types. */
private[json] object FundamentalCodec {

  /** Get a codec for a fundamental data type. */
  def getFundamentalCodec(ft: FundamentalType): Encoder[Element] with Decoder[Element] = {
    ft match {
      case FundamentalType.Uint8      => uint8Codec
      case FundamentalType.Int8       => int8Codec
      case FundamentalType.Uint32     => uint32Codec
      case FundamentalType.Int32      => int32Codec
      case FundamentalType.Uint64     => uint64Codec
      case FundamentalType.Int64      => int64Codec
      case FundamentalType.Float32    => float32Codec
      case FundamentalType.Float64    => float64Codec
      case FundamentalType.StringType => stringCodec
      case FundamentalType.VoidType   => unitCodec
      case FundamentalType.BoolType   => boolCodec
    }
  }

  /** A Simple fundamental codec with plain encoding/decoding. */
  class SimpleFundamentalCodec[FT <: FundamentalType, ST](
      implicit aux: PrimitiveEncoder.Aux[FT, ST],
      encoder: Encoder[ST],
      decoder: Decoder[ST]
  ) extends Encoder[Element]
      with Decoder[Element] {
    override def apply(a: Element): Json = {
      a.asInstanceOf[Primitive[ST]].x.asJson
    }

    override def apply(c: HCursor): Result[Element] = {
      c.value.as[ST].map(Primitive(_))
    }
  }

  private val int8Codec = new SimpleFundamentalCodec[FundamentalType.Int8.type, Byte]
  private val int32Codec = new SimpleFundamentalCodec[FundamentalType.Int32.type, Int]
  private val int64Codec = new SimpleFundamentalCodec[FundamentalType.Int64.type, Long]
  private val float32Codec = new SimpleFundamentalCodec[FundamentalType.Float32.type, Float]
  private val float64Codec = new SimpleFundamentalCodec[FundamentalType.Float64.type, Double]
  private val stringCodec = new SimpleFundamentalCodec[FundamentalType.StringType.type, String]
  private val unitCodec = new SimpleFundamentalCodec[FundamentalType.VoidType.type, Unit]
  private val boolCodec = new SimpleFundamentalCodec[FundamentalType.BoolType.type, Boolean]

  private val uint8Codec = new Encoder[Element] with Decoder[Element] {
    override def apply(a: Element): Json = {
      java.lang.Byte.toUnsignedInt(a.asInstanceOf[Primitive[Byte]].x).asJson
    }

    override def apply(c: HCursor): Result[Element] = {
      c.value.as[Int].flatMap { r =>
        if (r < 0 || r > 255) {
          Left(DecodingFailure("Uint8 out of range", c.history))
        } else {
          Right(Primitive(r.toByte))
        }
      }
    }
  }

  private val uint32Codec = new Encoder[Element] with Decoder[Element] {
    override def apply(a: Element): Json = {
      java.lang.Integer.toUnsignedLong(a.asInstanceOf[Primitive[Int]].x).asJson
    }

    override def apply(c: HCursor): Result[Element] = {
      c.value.as[Long].flatMap { r =>
        if (r < 0 || r > 0xffffffffL) {
          Left(DecodingFailure("Uint32 out of range", c.history))
        } else {
          Right(Primitive(r.toInt))
        }
      }
    }
  }

  private val uint64Codec = new Encoder[Element] with Decoder[Element] {
    override def apply(a: Element): Json = {
      val stringValue = java.lang.Long.toUnsignedString(a.asInstanceOf[Primitive[Long]].x)
      val bigInt = BigInt(stringValue)
      Json.fromBigInt(bigInt)
    }

    override def apply(c: HCursor): Result[Element] = {
      c.value.as[BigInt].flatMap { r =>
        try {
          Right(Primitive(java.lang.Long.parseUnsignedLong(r.toString())))
        } catch {
          case e: NumberFormatException =>
            Left(DecodingFailure(e.getMessage, c.history))
        }
      }
    }
  }
}
