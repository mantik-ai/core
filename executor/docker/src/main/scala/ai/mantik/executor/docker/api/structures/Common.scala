package ai.mantik.executor.docker.api.structures

import io.circe.generic.JsonCodec

// Note: Docker uses upper and lower case
// We reflect this in case classes to use auto-codecs.

@JsonCodec
case class VersionResponse(
    Version: String,
    Arch: String,
    KernelVersion: String,
    ApiVersion: String
)
