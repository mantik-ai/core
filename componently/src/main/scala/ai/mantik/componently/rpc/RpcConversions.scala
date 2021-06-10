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
package ai.mantik.componently.rpc

import java.time.Instant

import akka.util.ByteString
import akka.util.ccompat.IterableOnce
import com.google.protobuf.{Timestamp, ByteString => ProtoByteString}
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
    bs.asByteBuffers.foldLeft(ProtoByteString.EMPTY) { case (c, n) =>
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
    Timestamp
      .newBuilder()
      .setSeconds(
        instant.getEpochSecond
      )
      .setNanos(instant.getNano)
      .build()
  }

  /** Decode protobuf timestamp into Java Instant */
  def decodeInstant(timestamp: Timestamp): Instant = {
    Instant.ofEpochSecond(
      timestamp.getSeconds,
      timestamp.getNanos
    )
  }

  /** Encode an error into a [[io.grpc.StatusRuntimeException]]. */
  def encodeError(e: Throwable, code: Code): StatusRuntimeException = {
    val description = e.getMessage // null is allowed according to source of Status.
    val status = code.toStatus.withDescription(description).withCause(e)
    new StatusRuntimeException(status)
  }
}
