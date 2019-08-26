package ai.mantik.executor.docker.api.structures

import java.time.Instant

import io.circe.generic.JsonCodec

@JsonCodec
case class InspectContainerResponse(
    Created: Instant,
    Image: String,
    Args: List[String]
)
