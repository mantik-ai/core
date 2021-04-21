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
