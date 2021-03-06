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
package ai.mantik.ds.formats.json

import ai.mantik.ds.Errors.EncodingException

import java.util.Base64
import ai.mantik.ds.converter.casthelper.TensorHelper
import ai.mantik.ds._
import ai.mantik.ds.element._
import ai.mantik.ds.helper.circe.CirceJson
import akka.stream.scaladsl.{Flow, JsonFraming, Source}
import akka.util.ByteString
import io.circe.Decoder.{Result, decodeJsonObject}
import io.circe._
import io.circe.syntax._
import io.circe.jawn
import cats.implicits._
import org.slf4j.LoggerFactory

/**
  * Bundle JSON Serializer.
  */
object JsonFormat extends Encoder.AsObject[Bundle] with Decoder[Bundle] {
  private val logger = LoggerFactory.getLogger(getClass)

  /** Raw Encoded value, used for decoding type in the first pass. */
  private case class Encoded(
      `type`: DataType,
      value: Json
  )

  private implicit val encodedCodec = CirceJson.makeSimpleCodec[Encoded]

  /** Serializes a bundle to JSON. */
  def serializeBundle(bundle: Bundle): Json = {
    Json.fromJsonObject(encodeObject(bundle))
  }

  /** Encode the bundle value (without type value). */
  def serializeBundleValue(bundle: Bundle): Json = {
    bundle match {
      case t: TabularBundle =>
        val rowEncoder = createRowEncoder(t.model)
        Json.arr(t.rows.map(row => rowEncoder(row)): _*)
      case s: SingleElementBundle =>
        val elementEncoder = createElementEncoder(s.model)
        elementEncoder.apply(s.element)
    }
  }

  /** Create an encoder for root elements. */
  def createRootElementEncoder(dataType: DataType): Encoder[RootElement] = {
    dataType match {
      case t: TabularData =>
        createRowEncoder(t).contramap(_.asInstanceOf[TabularRow])
      case otherwise =>
        createElementEncoder(otherwise).contramap[RootElement](_.asInstanceOf[SingleElement].element)
    }
  }

  /** Create a stream encoder (without header) */
  def createStreamRootElementEncoder(dataType: DataType): Flow[RootElement, ByteString, _] = {
    val encoder = createRootElementEncoder(dataType)
    val basicFlow = Flow.fromFunction { x: RootElement =>
      ByteString(encoder.apply(x).toString())
    }
    dataType match {
      case t: TabularData => basicFlow.intersperse(ByteString("["), ByteString(","), ByteString("]"))
      case otherwise      => basicFlow
    }
  }

  /** Create a stream encoder (with header) */
  def createStreamRootElementEncoderWithHeader(dataType: DataType): Flow[RootElement, ByteString, _] = {
    val valueEncoder = createStreamRootElementEncoder(dataType)
    val encodedType = dataType.asJson
    val header = s"""{"type":${encodedType},"value":"""
    val footer = "}"
    valueEncoder.prepend(Source.single(ByteString(header))) ++ Source.single(ByteString(footer))
  }

  /** Create a decoder for root elements. */
  def createRootElementDecoder(dataType: DataType): Decoder[RootElement] = {
    dataType match {
      case t: TabularData =>
        createRowDecoder(t).map(x => x: RootElement)
      case other =>
        createElementDecoder(other).map { e => SingleElement(e) }
    }
  }

  /** Create a stream decoder for root elements (without header) */
  def createStreamRootElementDecoder(dataType: DataType): Flow[ByteString, RootElement, _] = {
    // TODO: This is not streaming, however we are using this Mime type only for constants
    // Note: JsonFraming doesn't like our array of elements, as it expects an array of objects
    logger.warn(s"No implementation of stream decoding, using fallback")
    Flow
      .apply[ByteString]
      .fold(ByteString.empty) { case (current, next) =>
        current ++ next
      }
      .map { concatenated =>
        (for {
          json <- jawn.parseByteBuffer(concatenated.toByteBuffer)
          parsed <- deserializeBundleValue(dataType, json)
        } yield parsed) match {
          case Left(error) => throw error
          case Right(value) if value.model != dataType =>
            throw new EncodingException(s"Header (type=${value.model}) has different type than expected (=${dataType})")
          case Right(ok) => ok
        }
      }
      .mapConcat(_.rows)
    /*
    // This doesn't work
    val rootDecoder = createRootElementDecoder(dataType)
    JsonFraming.objectScanner(Short.MaxValue).map { frame =>
      (for {
        json <- jawn.parseByteBuffer(frame.toByteBuffer)
        parsed <- rootDecoder.decodeJson(json)
      } yield parsed) match {
        case Left(error) => throw error
        case Right(ok)   => ok
      }
    }
     */
  }

  /** Create a stream decoder for root elements (with header) */
  def createStreamRootElementDecoderWithHeader(dataType: DataType): Flow[ByteString, RootElement, _] = {
    // TODO: This is not streaming, however we are using this Mime type only for constants
    // We have no possibility to split out the JSON stream into separate key values without consuming all of them
    // JsonFraming is of no use here. Go implements one
    // However this is only needed for debugging Bridges and not in production
    logger.warn(s"No implementation of stream decoding with header, using fallback")
    Flow
      .apply[ByteString]
      .fold(ByteString.empty) { case (current, next) =>
        current ++ next
      }
      .map { concatenated =>
        (for {
          json <- jawn.parseByteBuffer(concatenated.toByteBuffer)
          parsed <- deserializeBundle(json)
        } yield parsed) match {
          case Left(error) => throw error
          case Right(value) if value.model != dataType =>
            throw new EncodingException(s"Header (type=${value.model}) has different type than expected (=${dataType})")
          case Right(ok) => ok
        }
      }
      .mapConcat(_.rows)
  }

  override def encodeObject(bundle: Bundle): JsonObject = {
    Encoded(
      bundle.model,
      serializeBundleValue(bundle)
    ).asJsonObject
  }

  /** Deserializes a Bundle from JSON. */
  def deserializeBundle(json: Json): Result[Bundle] = {
    for {
      encoded <- json.as[Encoded]
      valueDecoded <- deserializeBundleValue(encoded.`type`, encoded.value)
    } yield valueDecoded
  }

  /** Deserializes a Bundle value from JSON. */
  def deserializeBundleValue(dataType: DataType, json: Json): Result[Bundle] = {
    dataType match {
      case t: TabularData =>
        val decoder = createRowDecoder(t)
        json.asArray match {
          case None => Left(DecodingFailure("Expected array of values for rows", Nil))
          case Some(values) =>
            decodeRows(values, decoder).map { rows =>
              TabularBundle(t, rows)
            }
        }
      case other =>
        val decoder = createElementDecoder(other)
        decoder.decodeJson(json).map { value =>
          SingleElementBundle(other, value)
        }
    }
  }

  override def apply(c: HCursor): Result[Bundle] = {
    deserializeBundle(c.value)
  }

  private def decodeRows(iterable: Iterable[Json], decoder: Decoder[TabularRow]): Result[Vector[TabularRow]] = {
    val builder = Vector.newBuilder[TabularRow]
    val it = iterable.iterator
    while (it.hasNext) {
      decoder.decodeJson(it.next()) match {
        case Left(error) => return Left(error)
        case Right(ok)   => builder += ok
      }
    }
    Right(builder.result())
  }

  private def createRowEncoder(tabularData: TabularData): Encoder[TabularRow] = {
    createTupleEncoder(tabularData.columns.values)
      .contramap[TabularRow](_.columns)
  }

  private def createTupleEncoder(fields: Iterable[DataType]): Encoder[IndexedSeq[Element]] = {
    val subEncoders = fields.map {
      createElementEncoder
    }.toVector
    Encoder { data: IndexedSeq[Element] =>
      Json.fromValues(
        data.zip(subEncoders).map { case (cell, encoder) =>
          encoder(cell)
        }
      )
    }
  }

  private def createElementEncoder(dataType: DataType): Encoder[Element] = {
    dataType match {
      case ft: FundamentalType => FundamentalCodec.getFundamentalCodec(ft)
      case t: Tensor =>
        val ftCodec = FundamentalCodec.getFundamentalCodec(t.componentType)
        val tensorUnpacker = TensorHelper.tensorUnpacker(t.componentType)
        new Encoder[Element] {
          override def apply(a: Element): Json = {
            val elements = tensorUnpacker(a.asInstanceOf[TensorElement[_]])
            Json.fromValues(elements.map(p => ftCodec.apply(p)))
          }
        }
      case i: Image =>
        new Encoder[Element] {
          override def apply(a: Element): Json = {
            byteStringCodec.apply(a.asInstanceOf[ImageElement].bytes)
          }
        }
      case t: TabularData =>
        val rowEncoder = createRowEncoder(t)
        new Encoder[Element] {
          override def apply(a: Element): Json = {
            val embedded = a.asInstanceOf[EmbeddedTabularElement]
            Json.fromValues(
              embedded.rows.map(rowEncoder.apply)
            )
          }
        }
      case n: Nullable =>
        val underlyingEncoder = createElementEncoder(n.underlying)
        new Encoder[Element] {
          override def apply(a: Element): Json = {
            val value = a.asInstanceOf[NullableElement]
            value match {
              case NullElement =>
                Json.Null
              case SomeElement(x) =>
                underlyingEncoder(x)
            }
          }
        }
      case t: ArrayT =>
        val underlyingEncoder = createElementEncoder(t.underlying)
        new Encoder[Element] {
          override def apply(a: Element): Json = {
            val value = a.asInstanceOf[ArrayElement]
            Json.arr(
              value.elements.map { element =>
                underlyingEncoder(element)
              }: _*
            )
          }
        }
      case n: Struct =>
        val underlyingEncoder = createTupleEncoder(n.fields.values)
        underlyingEncoder.contramap[Element] { e =>
          e.asInstanceOf[StructElement].elements
        }
    }
  }

  private def createRowDecoder(tabularData: TabularData): Decoder[TabularRow] = {
    createStructDecoder(tabularData.columns.values).map(TabularRow(_))
  }

  private def createStructDecoder(dataTypes: Iterable[DataType]): Decoder[Vector[Element]] = {
    val cellDecoders: Vector[Decoder[Element]] = dataTypes.map {
      createElementDecoder
    }.toVector
    val size = cellDecoders.size
    new Decoder[Vector[Element]] {
      override def apply(c: HCursor): Result[Vector[Element]] = {
        c.values match {
          case None => Left(DecodingFailure.apply("Expected array", c.history))
          case Some(values) if values.size != size =>
            Left(DecodingFailure.apply(s"Expected array of size ${size}, got ${values.size}", c.history))
          case Some(values) =>
            values.toVector
              .zip(cellDecoders)
              .map { case (value, decoder) =>
                decoder.decodeJson(value)
              }
              .sequence
        }
      }
    }
  }

  private def createElementDecoder(dataType: DataType): Decoder[Element] = {
    dataType match {
      case ft: FundamentalType => FundamentalCodec.getFundamentalCodec(ft)
      case image: Image =>
        byteStringCodec.map(byteString => ImageElement(byteString): Element)
      case tensor: Tensor =>
        createTensorDecoder(tensor)
      case tabularData: TabularData =>
        val rowDecoder = createRowDecoder(tabularData)
        new Decoder[Element] {
          override def apply(c: HCursor): Result[Element] = {
            c.values match {
              case None => Left(DecodingFailure("Expected array", c.history))
              case Some(values) =>
                flatEitherApply(values, rowDecoder.decodeJson).map(EmbeddedTabularElement(_))
            }
          }
        }
      case nullable: Nullable =>
        val underlyingDecoder = createElementDecoder(nullable.underlying)
        new Decoder[Element] {
          override def apply(c: HCursor): Result[Element] = {
            if (c.value.isNull) {
              Right(NullElement)
            } else {
              underlyingDecoder(c).map(SomeElement.apply)
            }
          }
        }
      case t: ArrayT =>
        val underlyingDecoder = createElementDecoder(t.underlying)
        Decoder.decodeVector[Element](underlyingDecoder).map { elements =>
          ArrayElement(elements)
        }
      case nt: Struct =>
        val underlying = createStructDecoder(nt.fields.values)
        underlying.map(StructElement(_))
    }
  }

  private def createTensorDecoder(tensor: Tensor): Decoder[Element] = {
    val componentDecoder = FundamentalCodec.getFundamentalCodec(tensor.componentType)
    val elementCount = tensor.packedElementCount.toInt
    val packer = TensorHelper.tensorPacker(tensor.componentType)
    new Decoder[Element] {
      override def apply(c: HCursor): Result[Element] = {
        c.values match {
          case None =>
            Left(DecodingFailure("Expected array of elements", c.history))
          case Some(values) =>
            val builder = Vector.newBuilder[Primitive[_]]
            builder.sizeHint(elementCount)
            val it = values.iterator
            while (it.hasNext) {
              componentDecoder.decodeJson(it.next()) match {
                case Left(error) => return Left(error)
                case Right(v)    => builder += v.asInstanceOf[Primitive[_]]
              }
            }
            val elements = builder.result()
            if (elements.length != elementCount) {
              return Left(
                DecodingFailure(s"Invalid element count ${elements.length}, expected ${elementCount}", c.history)
              )
            }
            Right(packer(elements))
        }
      }
    }
  }

  private val base64Encoder = Base64.getEncoder
  private val base64Decoder = Base64.getDecoder

  private val byteStringCodec = new Encoder[ByteString] with Decoder[ByteString] {
    override def apply(a: ByteString): Json = {
      Json.fromString(base64Encoder.encodeToString(a.toArray))
    }

    override def apply(c: HCursor): Result[ByteString] = {
      c.value.as[String].flatMap { s =>
        try {
          Right(ByteString(base64Decoder.decode(s)))
        } catch {
          case e: Exception =>
            Left(DecodingFailure(e.getMessage, c.history))
        }
      }
    }
  }

  /** Applies the function f to each element of in, returns Left on the first error. */
  private def flatEitherApply[A, B, E](in: Iterable[A], f: A => Either[E, B]): Either[E, Vector[B]] = {
    val resultBuilder = Vector.newBuilder[B]
    val inIt = in.iterator
    while (inIt.hasNext) {
      f(inIt.next()) match {
        case Left(e)  => return Left(e)
        case Right(v) => resultBuilder += v
      }
    }
    Right(resultBuilder.result())
  }
}
