package ai.mantik.executor.model

import io.circe.{ Decoder, Encoder, Json }

import scala.language.implicitConversions

/**
 * Wraps some plain type in a way that it's serialized as a string.
 * Used for encoding values in Strings (e.g. Query Parameters)
 */
case class StringWrapped[T](
    value: T
)

object StringWrapped {
  implicit def fromValue[T](value: T): StringWrapped[T] = {
    StringWrapped(value)
  }

  implicit def encode[T](implicit e: Encoder[T]): Encoder[StringWrapped[T]] = {
    Encoder { v =>
      Json.fromString(e(v.value).toString())
    }
  }

  implicit def decode[T](implicit e: Decoder[T]): Decoder[StringWrapped[T]] = {
    Decoder.decodeString.emap { string =>
      for {
        json <- io.circe.parser.parse(string).left.map(_.toString)
        value <- e.decodeJson(json).left.map(_.toString)
      } yield {
        StringWrapped(value)
      }
    }
  }
}

