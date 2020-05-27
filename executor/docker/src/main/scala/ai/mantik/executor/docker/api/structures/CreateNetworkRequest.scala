package ai.mantik.executor.docker.api.structures

import io.circe.generic.JsonCodec

@JsonCodec
case class CreateNetworkRequest(
    Name: String,
    CheckDuplicate: Boolean = true,
    Driver: Option[String] = None, // defaults to Bridge
    Internal: Option[Boolean] = None,
    Labels: Map[String, String] = Map.empty
)

@JsonCodec
case class CreateNetworkResponse(
    Id: String,
    Warning: Option[String] = None
)
