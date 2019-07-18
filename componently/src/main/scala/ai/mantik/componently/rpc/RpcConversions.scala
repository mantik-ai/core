package ai.mantik.componently.rpc

import akka.util.ByteString
import akka.util.ccompat.IterableOnce
import com.google.protobuf.{ ByteString => ProtoByteString }

/** Helper for converting gRpc Primitives into Scala Objects and back. */
object RpcConversions {

  /** Decode a Protobuf Bytestring into Akka variant. */
  def decodeByteString(bs: ProtoByteString): ByteString = {
    ByteString.fromArrayUnsafe(bs.toByteArray)
  }

  /** Encode Akka ByteString into Protobuf variant. */
  def encodeByteString(bs: ByteString): ProtoByteString = {
    bs.asByteBuffers.foldLeft(ProtoByteString.EMPTY) {
      case (c, n) =>
        c.concat(ProtoByteString.copyFrom(n))
    }
  }

  /** Encode many ByteStrings into Protobuf variant. */
  def encodeByteString(bs: IterableOnce[ByteString]): ProtoByteString = {
    var result = ProtoByteString.EMPTY
    for {
      b <- bs
      p <- b.asByteBuffers
    } {
      result = result.concat(ProtoByteString.copyFrom(p))
    }
    result
  }

  /** Encode an Optional string, None is encoded as "". */
  def encodeOptionalString(s: Option[String]): String = s.getOrElse("")

  /** Decode an optional string, when empty it returns a None. */
  def decodeOptionalString(str: String): Option[String] = {
    if (str.isEmpty) {
      None
    } else {
      Some(str)
    }
  }
}
