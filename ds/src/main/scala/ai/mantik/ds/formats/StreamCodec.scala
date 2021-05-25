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
package ai.mantik.ds.formats

import ai.mantik.ds.{DataType, Errors}
import ai.mantik.ds.element.RootElement
import ai.mantik.ds.formats.json.JsonFormat
import ai.mantik.ds.formats.messagepack.MessagePackReaderWriter
import akka.stream.javadsl.JsonFraming
import akka.stream.scaladsl.Flow
import akka.util.ByteString

/** Builtin codecs for streaming data */
object StreamCodec {
  sealed abstract class CodecMimeType(val name: String)

  /** Plain JSON */
  object MimeJson extends CodecMimeType("application/json")

  /** Mantik JSON Bundle with Meta */
  object MimeMantikBundleJson extends CodecMimeType("application-x-mantik-bundle-json")

  /** Plain MsgPack */
  object MimeMsgPack extends CodecMimeType("application/x-msgpack")

  /** Mantik Natural Bundle with Meta */
  object MimeMantikBundle extends CodecMimeType("application/x-mantik-bundle")

  val MimeTypes = Seq(MimeJson, MimeMantikBundleJson, MimeMsgPack, MimeMantikBundle)

  /** Creates an encoder for given mime type. */
  def encoder(dataType: DataType, mimeType: CodecMimeType): Flow[RootElement, ByteString, _] = {
    mimeType match {
      case MimeJson =>
        JsonFormat.createStreamRootElementEncoder(dataType)
      case MimeMantikBundleJson =>
        JsonFormat.createStreamRootElementEncoderWithHeader(dataType)
      case MimeMsgPack =>
        new MessagePackReaderWriter(dataType, false).encoder()
      case MimeMantikBundle =>
        new MessagePackReaderWriter(dataType, true).encoder()
    }
  }

  /** Creates a encoder for given mime type */
  @throws[Errors.FormatNotSupportedException]
  def encoder(dataType: DataType, contentType: String): Flow[RootElement, ByteString, _] = {
    encoder(dataType, findMimeType(contentType))
  }

  /** Creates a decoder for given mime type. */
  def decoder(dataType: DataType, mimeType: CodecMimeType): Flow[ByteString, RootElement, _] = {
    mimeType match {
      case MimeJson =>
        JsonFormat.createStreamRootElementDecoder(dataType)
      case MimeMantikBundleJson =>
        JsonFormat.createStreamRootElementDecoderWithHeader(dataType)
      case MimeMsgPack =>
        new MessagePackReaderWriter(dataType, false).decoder()
      case MimeMantikBundle =>
        new MessagePackReaderWriter(dataType, true).decoder()
    }
  }

  /** Creates a decoder for given mime type. */
  @throws[Errors.FormatNotSupportedException]
  def decoder(dataType: DataType, contentType: String): Flow[ByteString, RootElement, _] = {
    decoder(dataType, findMimeType(contentType))
  }

  /** Find the codec for given content type */
  @throws[Errors.FormatNotSupportedException]
  private def findMimeType(contentType: String): CodecMimeType = {
    MimeTypes.find(_.name == contentType).getOrElse {
      throw new Errors.FormatNotSupportedException(s"Format ${contentType} not supported")
    }
  }
}
