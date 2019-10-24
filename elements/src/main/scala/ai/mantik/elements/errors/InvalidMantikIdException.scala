package ai.mantik.elements.errors

import io.circe.DecodingFailure

/** An invalid mantik id. */
case class InvalidMantikIdException(message: String, private val cause: Throwable = null) extends MantikException(message, cause) {
  /** Wrap in io.circe Decoding Failure. */
  def wrapInDecodingFailure: DecodingFailure = {
    DecodingFailure(message, Nil)
  }
}

object InvalidMantikIdException {
  /** Convert from Decoding failure */
  def fromDecodingFailure(decodingFailure: DecodingFailure): InvalidMantikIdException = {
    InvalidMantikIdException(decodingFailure.message)
  }
}
