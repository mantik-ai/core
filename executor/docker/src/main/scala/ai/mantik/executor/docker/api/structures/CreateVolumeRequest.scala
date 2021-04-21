package ai.mantik.executor.docker.api.structures

import io.circe.generic.JsonCodec

@JsonCodec
case class CreateVolumeRequest(
    Name: String,
    Labels: Map[String, String] = Map.empty,
    Driver: String = "local"
)

@JsonCodec
case class CreateVolumeResponse(
    Name: String,
    Driver: String,
    Mountpoint: String,
    Status: Option[Map[String, String]] = None,
    Labels: Option[Map[String, String]] = None
)
