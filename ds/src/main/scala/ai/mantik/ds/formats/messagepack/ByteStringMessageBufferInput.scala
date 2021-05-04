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

import akka.util.ByteString
import org.msgpack.core.buffer.{MessageBuffer, MessageBufferInput}

import java.nio.ByteBuffer

/**
  * Wraps a ByteString as MessageBuffer Input.
  * Note: data is read directly, do not modify MessageBufferInput or data arrays
  */
private[messagepack] class ByteStringMessageBufferInput(byteString: ByteString) extends MessageBufferInput {
  val iterator = byteString.asByteBuffers.iterator

  override def next(): MessageBuffer = {
    if (iterator.hasNext) {
      val value = iterator.next()
      value match {
        case b if b.isDirect || b.hasArray => MessageBuffer.wrap(b)
        case b =>
          val copied = ByteBuffer.allocate(b.capacity())
          copied.mark()
          copied.put(b)
          copied.reset()
          MessageBuffer.wrap(copied)
      }
    } else {
      null
    }
  }

  override def close(): Unit = ()
}
