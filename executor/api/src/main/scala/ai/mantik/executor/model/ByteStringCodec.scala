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
