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
package ai.mantik.ds.formats.messagepack

import ai.mantik.ds.DataType
import ai.mantik.ds.Errors.{EncodingException, FormatDefinitionException}
import ai.mantik.ds.helper.akka.MessagePackFramer
import ai.mantik.ds.helper.circe.MessagePackJsonSupport
import ai.mantik.ds.element.{Bundle, RootElement}
import akka.stream._
import akka.stream.scaladsl.{FileIO, Flow, Keep, Sink, Source}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.util.ByteString
import org.msgpack.core.{MessagePack, MessageUnpacker}
import io.circe.syntax._
import org.msgpack.core.buffer.{MessageBuffer, MessageBufferInput}

import scala.jdk.CollectionConverters._
import scala.concurrent.{Future, Promise}

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

    val elementsWithoutHeader: Flow[ByteString, ByteString, _] =
      messagePackFramer.prefixAndTail(1).flatMapConcat { case (header, followUp) =>
        val parsedHeader = MessagePackJsonSupport.fromMessagePackBytes(header.head).as[Header].getOrElse {
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

  /** Decode directly from a bytestring. */
  def decodeFromByteString(byteString: ByteString): Bundle = {
    val input = new ByteStringMessageBufferInput(byteString)
    val unpacker = MessagePack.newDefaultUnpacker(input)
    decodeFromMessageUnpacker(unpacker)
  }

  /** Decode directly from Message Buffer input */
  private[messagepack] def decodeFromMessageUnpacker(unpacker: MessageUnpacker): Bundle = {
    if (withHeader) {
      val json = MessagePackJsonSupport.readJsonToMessagePack(unpacker)
      val header = json.as[Header].getOrElse {
        throw new EncodingException(s"Could not decode header")
      }
      if (header.format != dataType) {
        throw new EncodingException(s"Format mismatch, expected: ${dataType}, got ${header.format}")
      }
    }
    val context = MessagePackAdapters.createRootElementContext(dataType)
    val resultBuilder = Vector.newBuilder[RootElement]
    while (unpacker.hasNext) {
      resultBuilder += context.read(unpacker)
    }
    Bundle(dataType, resultBuilder.result())
  }

  /** Generate a pure encoder for RootElemensts. */
  def encoder(): Flow[RootElement, ByteString, _] = {
    // Row Writer
    val naturalTabularRowWriter = Flow.fromGraph(
      new NaturalTabularRowWriter(
        MessagePackAdapters.createRootElementContext(dataType)
      )
    )

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

  /** Encode elements to bytestring. */
  def encodeToByteString(elements: Seq[RootElement]): ByteString = {
    val context = MessagePackAdapters.createRootElementContext(dataType)

    val header = if (withHeader) {
      MessagePackJsonSupport.toMessagePackBytes(Header(dataType).asJson)
    } else {
      ByteString.empty
    }

    val packer = MessagePack.newDefaultBufferPacker()
    elements.foreach { element =>
      context.write(packer, element)
    }

    val buffers = packer.toBufferList

    var result = header
    buffers.asScala.foreach { messageBuffer =>
      result = result ++ ByteString.fromArrayUnsafe(messageBuffer.array(), 0, messageBuffer.size())
    }
    result
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
    val result = Promise[DataType]()

    val decoded: Flow[ByteString, RootElement, _] =
      messagePackFramer.prefixAndTail(1).flatMapConcat { case (header, followUp) =>
        val asJson =
          try {
            MessagePackJsonSupport.fromMessagePackBytes(header.head)
          } catch {
            case e: Exception =>
              val exc = new EncodingException("Invalid Header", e)
              result.tryFailure(exc)
              throw exc
          }
        val parsedHeader = asJson.as[Header].getOrElse {
          val exc = new EncodingException(s"Could not parse header")
          result.tryFailure(exc)
          throw exc
        }
        result.trySuccess(parsedHeader.format)
        val context = MessagePackAdapters.createRootElementContext(parsedHeader.format)
        followUp
          .map { byteString =>
            byteString.toArray
            val unpacker = MessagePack.newDefaultUnpacker(byteString.toArray) // TODO: Fix extra allocation
            context.read(unpacker)
          }
          .mapMaterializedValue(_ => Int)
      }

    decoded.mapMaterializedValue(_ => result.future)
  }

  /** Decodes from a bytestring with type deduction from header */
  def autoFormatDecoderFromByteString(byteString: ByteString): Bundle = {
    val input = new ByteStringMessageBufferInput(byteString)
    val unpacker = MessagePack.newDefaultUnpacker(input)
    val json = MessagePackJsonSupport.readJsonToMessagePack(unpacker)
    val header = json.as[Header].getOrElse {
      throw new EncodingException(s"Could not decode header")
    }
    new MessagePackReaderWriter(header.format, withHeader = false)
      .decodeFromMessageUnpacker(unpacker)
  }
}

private[messagepack] class NaturalTabularRowWriter(context: MessagePackAdapters.RootElementContext)
    extends GraphStage[FlowShape[RootElement, ByteString]] {
  val in: Inlet[RootElement] = Inlet("NaturalTabularRowWriter.in")
  val out: Outlet[ByteString] = Outlet("NaturalTabularRowWriter.out")

  override val shape = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape)
    with InHandler
    with OutHandler {
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
