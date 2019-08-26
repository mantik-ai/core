package ai.mantik.executor.docker.api

import io.circe.generic.JsonCodec

/** Docker Error Response. */
@JsonCodec
case class ErrorResponse(
    message: String
)