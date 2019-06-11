package ai.mantik.ds.element

import java.nio.file.Path

import ai.mantik.ds._
import ai.mantik.ds.converter.Cast.findCast
import ai.mantik.ds.converter.StringPreviewGenerator
import ai.mantik.ds.formats.json.JsonFormat
import ai.mantik.ds.formats.natural.{ NaturalFormatDescription, NaturalFormatReaderWriter }
import ai.mantik.ds.helper.ZipUtils
import akka.stream.scaladsl.{ Compression, FileIO, Keep, Sink, Source }
import akka.stream.{ IOResult, Materializer }
import akka.util.ByteString
import io.circe.{ Decoder, Encoder, ObjectEncoder }

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, ExecutionContext, Future }

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
  /**
   * Export the bundle into single file ZIP File.
   * Note: this is intended for testing.
   * @return future which completes when the file is written and with the generated description.
   */
  def toZipBundle(zipOutputFile: Path)(implicit materializer: Materializer): Future[(NaturalFormatDescription, IOResult)] = {
    implicit val ec = materializer.executionContext
    val description = NaturalFormatDescription(
      model = model,
      file = Some(Bundle.DefaultFileName)
    )
    val sink = FileIO.toPath(zipOutputFile)
    val source = Source(rows)
    val encoder = new NaturalFormatReaderWriter(description).encoder()
    val zipper = ZipUtils.zipSingleFileStream(description.file.get)

    val ioResultFuture = source.via(encoder).via(zipper).runWith(sink)
    ioResultFuture.map { ioResult =>
      description -> ioResult
    }
  }

  /** Serializes the bundle into a Gzip Stream. */
  def asGzip(): Source[ByteString, _] = {
    val source = Source(rows)
    val description = NaturalFormatDescription(model)
    val encoder = new NaturalFormatReaderWriter(description).encoder()
    val gzipper = Compression.gzip
    source.via(encoder).via(gzipper)
  }

  /** Encode as stream  */
  def encode(withHeader: Boolean): Source[ByteString, _] = {
    val source = Source(rows)
    val description = NaturalFormatDescription(model)
    val encoder = new NaturalFormatReaderWriter(description, withHeader).encoder()
    source.via(encoder)
  }

  /** Serializes the bundle into a Gzip Block. */
  def asGzipSync()(implicit materializer: Materializer): ByteString = {
    Await.result(asGzip().runWith(Sink.seq[ByteString]), Duration.Inf).fold(ByteString.empty)(_ ++ _)
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
}

/** A  Bundle which contains tabular data. */
case class TabularBundle(
    model: TabularData,
    rows: Vector[TabularRow]
) extends Bundle {
  override def single: Option[Element] = None

  /**
   * Convert a tabular bundle into a single element bundle by inserting a wrapped tabular element.
   * Note: this is in most cases practical, as tabular bundles are more efficient to encode.
   * However during JSON Serialization/Deserialization this can be practical.
   */
  def toSingleElementBundle: SingleElementBundle = SingleElementBundle(model, EmbeddedTabularElement(rows))
}

object Bundle {

  /** File name for used in toZipBundle operations. */
  val DefaultFileName = "file.dat"

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

  /**
   * Import the natural bundle from a single file ZIP File.
   * This is mainly intended for testing.
   */
  def fromZipBundle(input: Path)(implicit materializer: Materializer): Future[Bundle] = {
    implicit val ec = materializer.executionContext
    val source = FileIO.fromPath(input)
    val unpacker = ZipUtils.unzipSingleFileStream()
    val decoder = NaturalFormatReaderWriter.autoFormatDecoder()

    val (formatFuture: Future[DataType], dataFuture: Future[Seq[RootElement]]) = source
      .via(unpacker)
      .viaMat(decoder)(Keep.right) // right element has format inside
      .toMat(Sink.seq)(Keep.both) // left element has format, right has data
      .run()
    for {
      format <- formatFuture
      data <- dataFuture
    } yield Bundle(
      format, data.toVector
    )
  }

  /** Deserializes the bundle from a GZIP Stream */
  def fromGzip()(implicit ec: ExecutionContext): Sink[ByteString, Future[Bundle]] = {
    val decoder = NaturalFormatReaderWriter.autoFormatDecoder()
    val sink: Sink[ByteString, (Future[DataType], Future[Seq[RootElement]])] =
      Compression.gunzip().viaMat(decoder)(Keep.right).toMat(Sink.seq)(Keep.both)
    sink.mapMaterializedValue {
      case (dataTypeFuture, elementsFuture) =>
        for {
          dataType <- dataTypeFuture
          elements <- elementsFuture
        } yield Bundle(dataType, elements.toVector)
    }
  }

  /** Deserializes the bundle from a stream without header. */
  def fromStreamWithoutHeader(dataType: DataType)(implicit ec: ExecutionContext): Sink[ByteString, Future[Bundle]] = {
    val readerWriter = new NaturalFormatReaderWriter(NaturalFormatDescription(dataType), withHeader = false)
    val sink: Sink[ByteString, Future[Seq[RootElement]]] = readerWriter.decoder().toMat(Sink.seq[RootElement])(Keep.right)
    sink.mapMaterializedValue { elementsFuture =>
      elementsFuture.map { elements =>
        Bundle(
          dataType,
          elements.toVector
        )
      }
    }
  }

  /** Deserializes from a Stream including Header. */
  def fromStreamWithHeader()(implicit ec: ExecutionContext): Sink[ByteString, Future[Bundle]] = {
    val decoder = NaturalFormatReaderWriter.autoFormatDecoder()
    val sink: Sink[ByteString, (Future[DataType], Future[Seq[RootElement]])] =
      decoder.toMat(Sink.seq)(Keep.both)
    sink.mapMaterializedValue {
      case (dataTypeFuture, elementsFuture) =>
        for {
          dataType <- dataTypeFuture
          elements <- elementsFuture
        } yield Bundle(dataType, elements.toVector)
    }
  }

  /** Deserializes the bundle from an in-memory bytestring. (gzipped) */
  def fromGzipSync(byteString: ByteString)(implicit materializer: Materializer): Bundle = {
    implicit val ec = materializer.executionContext
    Await.result(Source.single(byteString).toMat(fromGzip())(Keep.right).run(), Duration.Inf)
  }

  /** Experimental Builder for Natural types. */
  class Builder(tabularData: TabularData) {
    private val rowBuilder = Vector.newBuilder[TabularRow]

    /** Add a row (just use the pure Scala Types, no Primitives or similar. */
    def row(values: Any*): Builder = {
      addCheckedRow(values)
      this
    }

    def result: TabularBundle = TabularBundle(
      tabularData, rowBuilder.result()
    )

    private def addCheckedRow(values: Seq[Any]): Unit = {
      require(values.length == tabularData.columns.size)
      val converted = values.zip(tabularData.columns).map {
        case (value, (columnName, pt: FundamentalType)) =>
          val encoder = PrimitiveEncoder.lookup(pt)
          require(encoder.convert.isDefinedAt(value), s"Value  ${value} of class ${value.getClass} must fit to ${pt}")
          encoder.wrap(encoder.convert(value))
        case (value, (columnName, i: Image)) =>
          require(value.isInstanceOf[ImageElement])
          value.asInstanceOf[ImageElement]
        case (value: EmbeddedTabularElement, (columnName, d: TabularData)) =>
          value
        case (value, (columnName, i: Tensor)) =>
          require(value.isInstanceOf[TensorElement[_]])
          value.asInstanceOf[Element]
        case (other, (columnName, dataType)) =>
          throw new IllegalArgumentException(s"Could not encode ${other} as ${dataType}")
      }
      rowBuilder += TabularRow(converted.toVector)
    }

  }

  /** Experimental builder for tabular data. */
  def build(tabularData: TabularData): Builder = new Builder(tabularData)

  def buildColumnWise: ColumnWiseBundleBuilder = ColumnWiseBundleBuilder()

  /** Build a non-tabular value. */
  def build(nonTabular: DataType, value: Element): SingleElementBundle = {
    require(!nonTabular.isInstanceOf[TabularData], "Builder can only be used for nontabular data")
    SingleElementBundle(nonTabular, value)
  }

  /** Wrap a single primitive non tabular value. */
  def fundamental[ST](x: ST)(implicit valueEncoder: ValueEncoder[ST]): SingleElementBundle = {
    SingleElementBundle(valueEncoder.fundamentalType, valueEncoder.wrap(x))
  }

  /** The empty value. */
  def void: SingleElementBundle = Bundle.build(FundamentalType.VoidType, Primitive.unit)

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

