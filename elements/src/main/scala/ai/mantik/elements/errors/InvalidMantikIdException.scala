package ai.mantik.elements.errors

import io.circe.DecodingFailure

/** An invalid mantik id. */
class InvalidMantikIdException(message: String, private val cause: Throwable = null)
    extends MantikException(ErrorCodes.InvalidMantikId, message, cause) {

  /** Wrap in io.circe Decoding Failure. */
  def wrapInDecodingFailure: DecodingFailure = {
    DecodingFailure(message, Nil)
  }
}

object InvalidMantikIdException {

  /** Convert from Decoding failure */
  def fromDecodingFailure(decodingFailure: DecodingFailure): InvalidMantikIdException = {
    new InvalidMantikIdException(decodingFailure.message)
  }
}
