package ai.mantik.elements.registry.api

import java.time.Instant

import io.circe.generic.JsonCodec

@JsonCodec
case class ApiLoginRequest(
    name: String,
    password: String,
    requester: String
)

@JsonCodec
case class ApiLoginResponse(
    token: String,
    validUntil: Option[Instant]
)

@JsonCodec
case class ApiLoginStatusResponse(
    tokenError: Option[String],
    validUntil: Option[Instant],
    valid: Boolean,
    accountEmail: Option[String]
)