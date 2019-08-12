package ai.mantik.elements.registry.api

import io.circe.generic.JsonCodec

@JsonCodec
case class ApiErrorResponse(
    code: String,
    message: Option[String]
)

object ApiErrorResponse {

  // Error codes

  val BadRequest = "BadRequest"

  val NotFound = "NotFound"

  val InvalidMantikfile = "InvalidMantikfile"

  val InvalidMantikId = "InvalidMantikId"

  val InvalidLogin = "InvalidLogin"

  val InvalidToken = "InvalidToken"
}