package ai.mantik.componently.rpc

import java.time.Instant

import akka.util.ByteString
import akka.util.ccompat.IterableOnce
import com.google.protobuf.{ Timestamp, ByteString => ProtoByteString }
import io.grpc.Status.Code
import io.grpc.StatusRuntimeException

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

  /** Encode a Java Instant into protobuf Timestamp */
  def encodeInstant(instant: Instant): Timestamp = {
    Timestamp.newBuilder().setSeconds(
      instant.getEpochSecond
    ).setNanos(instant.getNano).build()
  }

  /** Decode protobuf timestamp into Java Instant */
  def decodeInstant(timestamp: Timestamp): Instant = {
    Instant.ofEpochSecond(
      timestamp.getSeconds,
      timestamp.getNanos
    )
  }

  /** Encode an error into a [[StatusRuntimeException]]. */
  def encodeError(e: Throwable, code: Code): StatusRuntimeException = {
    val description = e.getMessage // null is allowed according to source of Status.
    val status = code.toStatus.withDescription(description).withCause(e)
    new StatusRuntimeException(status)
  }
}
