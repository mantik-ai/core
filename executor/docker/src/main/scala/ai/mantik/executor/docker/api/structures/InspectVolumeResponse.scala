package ai.mantik.executor.docker.api.structures

import io.circe.generic.JsonCodec

@JsonCodec
case class InspectVolumeResponse(
    Name: String,
    Driver: String
)
