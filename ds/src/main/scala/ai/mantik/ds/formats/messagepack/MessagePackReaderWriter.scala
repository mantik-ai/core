package ai.mantik.ds.formats.messagepack

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

/**
 * Reader/Writer for Message Pack Bundles.
 * (Also called "natural" Bundles when being referred).
 */
class MessagePackReaderWriter(dataType: DataType, withHeader: Boolean = true) {

  /** Create a pure decoder for RootElements. */
  def decoder(): Flow[ByteString, RootElement, _] = {
    val messagePackFramer = MessagePackFramer.make()
    val context = MessagePackAdapters.createRootElementContext(dataType)

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
        if (parsedHeader.format != dataType) {
          throw new EncodingException(s"Format mismatch, expected: ${dataType}, got ${parsedHeader.format}")
        }
        followUp
    }

    val unpacked = elementsWithoutHeader.map { byteString =>
      val unpacker = MessagePack.newDefaultUnpacker(byteString.toArray)
      context.read(unpacker)
    }

    unpacked
  }

  /** Generate a pure encoder for RootElemensts. */
  def encoder(): Flow[RootElement, ByteString, _] = {
    // Row Writer
    val naturalTabularRowWriter = Flow.fromGraph(new NaturalTabularRowWriter(
      MessagePackAdapters.createRootElementContext(dataType)
    ))

    if (!withHeader) {
      return naturalTabularRowWriter
    }

    // Header
    val prefix = MessagePackJsonSupport.toMessagePackBytes(
      Header(dataType).asJson
    )

    // Prepending header
    val prepended: Flow[RootElement, ByteString, _] = naturalTabularRowWriter.prepend(Source(Vector(prefix)))
    prepended
  }
}

object MessagePackReaderWriter {

  /**
   * Create a pure decoder for RootElements.
   * The format will be directly read from the header.
   * The format will be returned as materialized value
   * Note: do not reuse flow, as it carries an internal state (the data type)
   */
  def autoFormatDecoder(): Flow[ByteString, RootElement, Future[DataType]] = {
    val messagePackFramer = MessagePackFramer.make()
    val result = Promise[DataType]

    val decoded: Flow[ByteString, RootElement, _] = messagePackFramer.prefixAndTail(1).flatMapConcat {
      case (header, followUp) =>
        val asJson = try {
          MessagePackJsonSupport.fromMessagePackBytes(header.head)
        } catch {
          case e: Exception =>
            val exc = new EncodingException("Invalid Header", e)
            result.tryFailure(exc)
            throw exc
        }
        val parsedHeader = asJson.as[Header].right.getOrElse {
          val exc = new EncodingException(s"Could not parse header")
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

private[messagepack] class NaturalTabularRowWriter(context: MessagePackAdapters.RootElementContext) extends GraphStage[FlowShape[RootElement, ByteString]] {
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