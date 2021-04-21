package ai.mantik.elements.registry.api

import io.circe.generic.JsonCodec

@JsonCodec
case class ApiErrorResponse(
    code: String,
    message: Option[String]
)

/** Contains error codes which are not part of [[ai.mantik.elements.errors.ErrorCodes]] */
object ApiErrorResponse {

  // Error codes

  /** There was a Bad Request. */
  val BadRequest = "BadRequest"

  /** Some resource not found. */
  val NotFound = "NotFound"

  /** There was no permission to access this element */
  val NoPermission = "NoPermission"

  val InvalidMantikHeader = "InvalidMantikHeader"

  val InvalidMantikId = "InvalidMantikId"

  /** The login was invalid */
  val InvalidLogin = "InvalidLogin"

  /** The token was invalid. */
  val InvalidToken = "InvalidToken"

}
