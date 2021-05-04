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
package ai.mantik.executor.model

import java.util.Base64

import akka.util.ByteString
import io.circe.{Decoder, Encoder, Json}

import scala.util.Try

/** A Base64 Encoding for ByteStrings. */
object ByteStringCodec {
  private val base64Encoder = Base64.getEncoder
  private val base64Decoder = Base64.getDecoder

  implicit val encoder: Encoder[ByteString] = Encoder.instance { bytes =>
    Json.fromString(base64Encoder.encodeToString(bytes.toArray[Byte]))
  }

  implicit val decoder: Decoder[ByteString] = Decoder.decodeString.emapTry { s =>
    Try {
      ByteString.fromArray(base64Decoder.decode(s))
    }
  }
}
