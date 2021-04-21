package ai.mantik.executor.docker.api.structures

import java.time.Instant

import io.circe.generic.JsonCodec

@JsonCodec
case class InspectContainerResponse(
    Id: String,
    Created: Instant,
    Image: String,
    Args: List[String],
    NetworkSettings: InspectContainerNetworkResponse
)

@JsonCodec
case class InspectContainerNetworkResponse(
    Networks: Map[String, InspectContainerNetworkSpecificResponse] = Map.empty
)

@JsonCodec
case class InspectContainerNetworkSpecificResponse(
    NetworkID: String
)
