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
package ai.mantik.ds.element

import ai.mantik.ds._
import ai.mantik.ds.converter.Cast.findCast
import ai.mantik.ds.converter.StringPreviewGenerator
import ai.mantik.ds.formats.json.JsonFormat
import ai.mantik.ds.formats.messagepack.MessagePackReaderWriter
import akka.stream.scaladsl.{Compression, FileIO, Keep, Sink, Source}
import akka.util.ByteString
import io.circe.{Decoder, Encoder, ObjectEncoder}

import scala.concurrent.{Await, ExecutionContext, Future}

/**
  * An in-memory data bundle
  *
  * This is either a [[TabularBundle]] or a [[SingleElementBundle]].
  */
sealed trait Bundle {

  /** The underlying data type. */
  def model: DataType

  /** Returns the rows of the bundle. In case of single elements Vector[SingleElement] is returned. */
  def rows: Vector[RootElement]

  /** Encode as stream */
  def encode(withHeader: Boolean): Source[ByteString, _] = {
    val source = Source(rows)
    val encoder = new MessagePackReaderWriter(model, withHeader).encoder()
    source.via(encoder)
  }

  /** Encode as ByteString. */
  def encodeAsByteString(withHeader: Boolean): ByteString = {
    new MessagePackReaderWriter(model, withHeader).encodeToByteString(rows)
  }

  /** Renders the Bundle. */
  def render(maxLines: Int = 20): String = {
    new StringPreviewGenerator(maxLines).render(this)
  }

  override def toString: String = {
    try {
      new StringPreviewGenerator().renderSingleLine(this)
    } catch {
      case e: Exception => "<Error Bundle>"
    }
  }

  /**
    * Returns the single element contained in the bundle.
    * This works only for Bundles which are not tabular.
    */
  def single: Option[Element]

  /**
    * Convert a tabular bundle into a single element bundle by inserting a wrapped tabular element.
    * SingleElementBundles are not touched.
    */
  def toSingleElementBundle: SingleElementBundle

  /** Sort the bundle (noop for SingleElementBundle) */
  def sorted: Bundle
}

/** A Bundle which contains a single element. */
case class SingleElementBundle(
    model: DataType,
    element: Element
) extends Bundle {
  override def rows: Vector[RootElement] = Vector(SingleElement(element))

  override def single: Option[Element] = Some(element)

  /**
    * Cast this bundle to a new type.
    * Note: loosing precision is only deducted from the types. It is possible
    * that a cast is marked as loosing precision but it's not in practice
    * (e.g. 100.0 (float64)--> 100 (int))
    * @param allowLoosing if true, it's allowed when the cast looses precision.
    */
  def cast(to: DataType, allowLoosing: Boolean = false): Either[String, SingleElementBundle] = {
    findCast(model, to) match {
      case Left(error) => Left(error)
      case Right(c) if !c.loosing || allowLoosing =>
        try {
          Right(SingleElementBundle(to, c.op(element)))
        } catch {
          case e: Exception =>
            Left(s"Cast failed ${e.getMessage}")
        }
      case Right(c) => Left("Cast would loose precision")
    }
  }

  override def toSingleElementBundle: SingleElementBundle = this

  override def sorted: SingleElementBundle = this
}

/** A  Bundle which contains tabular data. */
case class TabularBundle(
    model: TabularData,
    rows: Vector[TabularRow]
) extends Bundle {
  override def single: Option[Element] = None

  override def toSingleElementBundle: SingleElementBundle = SingleElementBundle(model, EmbeddedTabularElement(rows))

  override def sorted: TabularBundle = {
    val ordering = ElementOrdering.tableRowOrdering(model)
    val sortedRows = rows.sorted(ordering)
    copy(rows = sortedRows)
  }
}

object TabularBundle {

  /** Builder for tabular data. */
  def build(tabularData: TabularData): TabularBuilder = new TabularBuilder(tabularData)

  def build(columns: (String, DataType)*): TabularBuilder = new TabularBuilder(TabularData(columns: _*))

  /** Builder for tabular data (column wise). */
  def buildColumnWise: ColumnWiseBundleBuilder = ColumnWiseBundleBuilder()

}

object Bundle {

  /**
    * Constructs a bundle from data type and elements.
    * @throws IllegalArgumentException if the bundle is invalid.
    */
  def apply(model: DataType, elements: Vector[RootElement]): Bundle = {
    elements match {
      case Vector(s: SingleElement) => SingleElementBundle(model, s.element)
      case rows =>
        val tabularRows = rows.collect {
          case r: TabularRow => r
          case _             => throw new IllegalArgumentException(s"Got a bundle non tabular rows, which have not count 1")
        }
        model match {
          case t: TabularData => TabularBundle(t, tabularRows)
          case _ =>
            throw new IllegalArgumentException("Got a non tabular bundle with tabular rows")
        }
    }
  }

  /** Deserializes the bundle from a stream without header. */
  def fromStreamWithoutHeader(dataType: DataType)(implicit ec: ExecutionContext): Sink[ByteString, Future[Bundle]] = {
    val readerWriter = new MessagePackReaderWriter(dataType, withHeader = false)
    val sink: Sink[ByteString, Future[Seq[RootElement]]] =
      readerWriter.decoder().toMat(Sink.seq[RootElement])(Keep.right)
    sink.mapMaterializedValue { elementsFuture =>
      elementsFuture.map { elements =>
        Bundle(
          dataType,
          elements.toVector
        )
      }
    }
  }

  /** Deserializes the bundle from a stream without header (as ByteString) */
  def fromByteStringWithoutHeader(dataType: DataType, bytes: ByteString): Bundle = {
    new MessagePackReaderWriter(dataType, withHeader = false).decodeFromByteString(bytes)
  }

  /** Deserializes from a Stream including Header. */
  def fromStreamWithHeader()(implicit ec: ExecutionContext): Sink[ByteString, Future[Bundle]] = {
    val decoder = MessagePackReaderWriter.autoFormatDecoder()
    val sink: Sink[ByteString, (Future[DataType], Future[Seq[RootElement]])] =
      decoder.toMat(Sink.seq)(Keep.both)
    sink.mapMaterializedValue { case (dataTypeFuture, elementsFuture) =>
      for {
        dataType <- dataTypeFuture
        elements <- elementsFuture
      } yield Bundle(dataType, elements.toVector)
    }
  }

  /** Deserializes from ByteString including header */
  def fromByteStringWithHeader(bytes: ByteString): Bundle = {
    MessagePackReaderWriter.autoFormatDecoderFromByteString(bytes)
  }

  /** Wrap a single primitive non tabular value. */
  def fundamental[ST](x: ST)(implicit valueEncoder: ValueEncoder[ST]): SingleElementBundle = {
    SingleElementBundle(valueEncoder.dataType, valueEncoder.wrap(x))
  }

  /** The empty value. */
  def void: SingleElementBundle = SingleElementBundle(FundamentalType.VoidType, Primitive.unit)

  /** A nullable void Null element value (for comparison with other nullable values) */
  def voidNull: SingleElementBundle = SingleElementBundle(Nullable(FundamentalType.VoidType), NullElement)

  /** JSON Encoder. */
  implicit val encoder: ObjectEncoder[Bundle] = JsonFormat

  /** JSON Decoder. */
  implicit val decoder: Decoder[Bundle] = JsonFormat
}

object SingleElementBundle {

  /** Encoder for SingleElementBundle. */
  implicit val encoder: Encoder[SingleElementBundle] = JsonFormat.contramap[SingleElementBundle](identity)

  implicit val decoder: Decoder[SingleElementBundle] = JsonFormat.map {
    case b: SingleElementBundle => b
    case b: TabularBundle       => b.toSingleElementBundle
  }
}
