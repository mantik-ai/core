package ai.mantik.ds.helper.messagepack

import java.nio.ByteOrder

import akka.util.ByteString
import org.msgpack.core.MessageFormat
import MessageFormat._

object MessagePackHelpers {

  /**
    * Returns the number of consumable bytes which form a single message pack message.
    * Note: code is partly copy and pasted (and converted to Scala) from MessagePack library code.
    * See MessageUnpacker.skipValue
    *
    * TODO: If MessagePack has ever an event driven approach https://github.com/msgpack/msgpack-java/issues/92
    * we can remove all this.
    */
  def consumableBytes(bytes: ByteString): Option[Int] = {
    implicit val byteOrdering = ByteOrder.BIG_ENDIAN
    val byteIterator = bytes.iterator
    var position = 0

    val readByte: () => Byte = { () =>
      if (!byteIterator.hasNext) {
        return None
      }
      val result = byteIterator.next()
      position = position + 1
      result
    }

    val readShort: () => Short = { () =>
      if (byteIterator.len < 2) {
        return None
      }
      val result = byteIterator.getShort
      position = position + 2
      result
    }

    val readInt: () => Int = { () =>
      if (byteIterator.len < 4) {
        return None
      }
      val result = byteIterator.getInt
      position = position + 4
      result
    }

    val skipPayload: Int => Unit = { elementCount =>
      if (byteIterator.len < elementCount) {
        return None
      } else {
        byteIterator.drop(elementCount)
        position += elementCount
      }
    }

    val readNextLength8: () => Int = { () =>
      readByte() & 0xff
    }

    val readNextLength16: () => Int = { () =>
      readShort() & 0xffff
    }

    val readNextLength32: () => Int = { () =>
      val result = readInt()
      if (result < 0) {
        throw new IllegalArgumentException(s"Overflow")
      }
      result
    }
    var count = 1
    while (count > 0) {
      val b = readByte()
      val f = MessageFormat.valueOf(b)
      f match {
        case POSFIXINT => // nothing
        case NEGFIXINT => // nothing
        case BOOLEAN   => // nothing
        case NIL       => // nothing
        case FIXMAP =>
          val mapLen = b & 0x0f
          count += mapLen * 2
        case FIXARRAY =>
          val arrayLen = b & 0x0f
          count += arrayLen
        case FIXSTR =>
          val strLen = b & 0x1f
          skipPayload(strLen)
        case INT8 | UINT8 =>
          skipPayload(1)
        case INT16 | UINT16 =>
          skipPayload(2)
        case INT32 | UINT32 | FLOAT32 =>
          skipPayload(4)
        case INT64 | UINT64 | FLOAT64 =>
          skipPayload(8)
        case BIN8 | STR8 =>
          skipPayload(readNextLength8())
        case BIN16 | STR16 =>
          skipPayload(readNextLength16())
        case BIN32 | STR32 =>
          skipPayload(readNextLength32())
        case FIXEXT1 =>
          skipPayload(2)
        case FIXEXT2 =>
          skipPayload(3)
        case FIXEXT4 =>
          skipPayload(5)
        case FIXEXT8 =>
          skipPayload(9)
        case FIXEXT16 =>
          skipPayload(17)
        case EXT8 =>
          skipPayload(readNextLength8() + 1)
        case EXT16 =>
          skipPayload(readNextLength16() + 1)
        case EXT32 =>
          skipPayload(readNextLength32() + 1)
        case ARRAY16 =>
          count += readNextLength16()
        case ARRAY32 =>
          count += readNextLength32()
        case MAP16 =>
          count += readNextLength16() * 2
        case MAP32 =>
          val diff = readNextLength32()
          if (diff > Int.MaxValue / 2) {
            throw new IllegalArgumentException("Overflow")
          }
          count += diff * 2
        case NEVER_USED =>
          throw new IllegalArgumentException("Bad data")
      }

      count -= 1
    }
    Some(position)
  }
}
