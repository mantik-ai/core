package ai.mantik.executor.docker.api.structures

import io.circe.generic.JsonCodec

@JsonCodec
case class ContainerWaitResponse(
    StatusCode: Int
)
