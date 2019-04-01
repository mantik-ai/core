package ai.mantik.ds.formats.natural

import java.nio.file.Path

import ai.mantik.ds.DataType
import ai.mantik.ds.Errors.{ EncodingException, FormatDefinitionException }
import ai.mantik.ds.helper.akka.MessagePackFramer
import ai.mantik.ds.helper.circe.MessagePackJsonSupport
import ai.mantik.ds.element.RootElement
import akka.stream._
import akka.stream.scaladsl.{ FileIO, Flow, Keep, Sink, Source }
import akka.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import akka.util.ByteString
import org.msgpack.core.MessagePack
import io.circe.syntax._

import scala.concurrent.{ Future, Promise }

/** Reader/Writer for natural format. */
class NaturalFormatReaderWriter(desc: NaturalFormatDescription, withHeader: Boolean = true) {

  /** Read a Natural Format unpacked Zip Bundle. */
  def readDirectory(directory: Path): Source[RootElement, _] = {
    val file = desc.file.getOrElse {
      throw new IllegalStateException(s"No file given, cannot read directory")
    }
    val inFile = directory.resolve(file)
    require(inFile.startsWith(directory), s"Input file is leaving directory, something is wrong with ${desc.file}")
    val fileSource = FileIO.fromPath(inFile)
    readInputSource(fileSource)
  }

  /** Read a plain input stream. */
  def readInputSource(source: Source[ByteString, _]): Source[RootElement, _] = {
    source.via(decoder())
  }

  /** Create a pure decoder for RootElemens. */
  def decoder(): Flow[ByteString, RootElement, _] = {
    val messagePackFramer = MessagePackFramer.make()
    val context = MessagePackAdapters.createRootElementContext(desc.model)

    if (!withHeader) {
      val unpacked = messagePackFramer.map { byteString =>
        val unpacker = MessagePack.newDefaultUnpacker(byteString.toArray)
        context.read(unpacker)
      }
      return unpacked
    }

    val elementsWithoutHeader: Flow[ByteString, ByteString, _] = messagePackFramer.prefixAndTail(1).flatMapConcat {
      case (header, followUp) =>
        val parsedHeader = MessagePackJsonSupport.fromMessagePackBytes(header.head).as[Header].right.getOrElse {
          throw new EncodingException(s"Could not parse header")
        }
        if (parsedHeader.format != desc.model) {
          // TODO: In future  we could do automatic sub selection here
          throw new FormatDefinitionException(s"Format mismatch, expected: ${desc.model}, got ${parsedHeader.format}")
        }
        followUp
    }

    val unpacked = elementsWithoutHeader.map { byteString =>
      val unpacker = MessagePack.newDefaultUnpacker(byteString.toArray)
      context.read(unpacker)
    }

    unpacked
  }

  /** Write into a Directory which can then be zipped. */
  def writeDirectory(directory: Path): Sink[RootElement, Future[IOResult]] = {
    val file = desc.file.getOrElse {
      throw new IllegalStateException(s"No file given, cannot read directory")
    }
    val outFile = directory.resolve(file)
    require(outFile.startsWith(directory), s"Output file is leaving directory, something is wrong with ${desc.file}")
    val fileSink = FileIO.toPath(outFile)

    encoder.toMat(fileSink)(Keep.right)
  }

  /** Generate a pure encoder for RootElemensts. */
  def encoder(): Flow[RootElement, ByteString, _] = {
    // Row Writer
    val naturalTabularRowWriter = Flow.fromGraph(new NaturalTabularRowWriter(
      MessagePackAdapters.createRootElementContext(desc.model)
    ))

    if (!withHeader) {
      return naturalTabularRowWriter
    }

    // Header
    val prefix = MessagePackJsonSupport.toMessagePackBytes(
      Header(desc.model).asJson
    )

    // Prepending header
    val prepended: Flow[RootElement, ByteString, _] = naturalTabularRowWriter.prepend(Source(Vector(prefix)))
    prepended
  }
}

object NaturalFormatReaderWriter {

  /**
   * Create a pure decoder for RootElemens.
   * The format will be directly read from the header.
   * The format will be returned as materialized value
   * Note: do not reuse flow, as it carries an internal state (the data type)
   */
  def autoFormatDecoder(): Flow[ByteString, RootElement, Future[DataType]] = {
    val messagePackFramer = MessagePackFramer.make()
    val result = Promise[DataType]

    val decoded: Flow[ByteString, RootElement, _] = messagePackFramer.prefixAndTail(1).flatMapConcat {
      case (header, followUp) =>
        val parsedHeader = MessagePackJsonSupport.fromMessagePackBytes(header.head).as[Header].right.getOrElse {
          val exc = throw new EncodingException(s"Could not parse header")
          result.tryFailure(exc)
          throw exc
        }
        result.trySuccess(parsedHeader.format)
        val context = MessagePackAdapters.createRootElementContext(parsedHeader.format)
        followUp.map { byteString =>
          byteString.toArray
          val unpacker = MessagePack.newDefaultUnpacker(byteString.toArray) // TODO: Fix extra allocation
          context.read(unpacker)
        }.mapMaterializedValue(_ => Int)
    }

    decoded.mapMaterializedValue(_ => result.future)
  }
}

private[natural] class NaturalTabularRowWriter(context: MessagePackAdapters.RootElementContext) extends GraphStage[FlowShape[RootElement, ByteString]] {
  val in: Inlet[RootElement] = Inlet("NaturalTabularRowWriter.in")
  val out: Outlet[ByteString] = Outlet("NaturalTabularRowWriter.out")

  override val shape = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) with InHandler with OutHandler {
    val bufferPacker = MessagePack.newDefaultBufferPacker()

    override def onPush(): Unit = {
      val input = grab(in)
      context.write(bufferPacker, input)
      val byteArray = bufferPacker.toByteArray() // according to docu, the array is newly created
      val converted = ByteString.fromArrayUnsafe(byteArray)
      push(out, converted)
      bufferPacker.clear()
    }

    override def onPull(): Unit = {
      pull(in)
    }

    setHandlers(in, out, this)
  }
}