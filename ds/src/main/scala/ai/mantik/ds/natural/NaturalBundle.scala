package ai.mantik.ds.natural

import java.nio.file.Path

import ai.mantik.ds.{ DataType, FundamentalType, Image, TabularData }
import ai.mantik.ds.formats.natural.{ NaturalFormatDescription, NaturalFormatReaderWriter }
import ai.mantik.ds.helper.ZipUtils
import akka.stream.scaladsl.{ Compression, FileIO, Keep, Sink, Source }
import akka.stream.{ IOResult, Materializer }
import akka.util.ByteString

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, ExecutionContext, Future }

/** An in-memory data bundle, used for testing. */
case class NaturalBundle(
    model: DataType,
    rows: Vector[RootElement]
) {
  /**
   * Export the natural bundle into single file ZIP File.
   * Note: this is intended for testing.
   * @return future which completes when the file is written and with the generated description.
   */
  def toZipBundle(zipOutputFile: Path)(implicit materializer: Materializer): Future[(NaturalFormatDescription, IOResult)] = {
    implicit val ec = materializer.executionContext
    val description = NaturalFormatDescription(
      model = model,
      file = Some(NaturalBundle.DefaultFileName)
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

  /** Serializes the bundle into a Gzip Block. */
  def asGzipSync()(implicit materializer: Materializer): ByteString = {
    Await.result(asGzip().runWith(Sink.seq[ByteString]), Duration.Inf).fold(ByteString.empty)(_ ++ _)
  }
}

object NaturalBundle {

  /** File name for used in toZipBundle operations. */
  val DefaultFileName = "file.dat"

  /**
   * Import the natural bundle from a single file ZIP File.
   * This is mainly intended for testing.
   */
  def fromZipBundle(input: Path)(implicit materializer: Materializer): Future[NaturalBundle] = {
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
    } yield NaturalBundle(
      format, data.toVector
    )
  }

  /** Deserializes the bundle from a GZIP Stream */
  def fromGzip()(implicit ec: ExecutionContext): Sink[ByteString, Future[NaturalBundle]] = {
    val decoder = NaturalFormatReaderWriter.autoFormatDecoder()
    val sink: Sink[ByteString, (Future[DataType], Future[Seq[RootElement]])] =
      Compression.gunzip().viaMat(decoder)(Keep.right).toMat(Sink.seq)(Keep.both)
    sink.mapMaterializedValue {
      case (dataTypeFuture, elementsFuture) =>
        for {
          dataType <- dataTypeFuture
          elements <- elementsFuture
        } yield NaturalBundle(dataType, elements.toVector)
    }
  }

  /** Deserializes the bundle from an in-memory bytestring. (gzipped) */
  def fromGzipSync(byteString: ByteString)(implicit materializer: Materializer): NaturalBundle = {
    implicit val ec = materializer.executionContext
    Await.result(Source.single(byteString).toMat(fromGzip())(Keep.right).run(), Duration.Inf)
  }

  /** Experimental Builder for Natural types. */
  class Builder(tabularData: TabularData) {
    val rowBuilder = Vector.newBuilder[RootElement]

    /** Add a row (just use the pure Scala Types, no Primitives or similar. */
    def row(values: Any*): Builder = {
      addCheckedRow(values)
      this
    }

    def result: NaturalBundle = NaturalBundle(
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
        case (other, (columnName, dataType)) =>
          throw new IllegalArgumentException(s"Could not encode ${other} as ${dataType}")
      }
      rowBuilder += TabularRow(converted.toVector)
    }

  }

  /** Experimental builder for tabular data. */
  def build(tabularData: TabularData): Builder = new Builder(tabularData)
}
