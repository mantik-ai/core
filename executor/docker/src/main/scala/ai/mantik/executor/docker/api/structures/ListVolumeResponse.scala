package ai.mantik.executor.docker.api.structures

import io.circe.generic.JsonCodec

@JsonCodec
case class ListVolumeResponse(
    Volumes: Vector[ListVolumeRow],
    Warnings: Option[Vector[String]] = None
)

@JsonCodec
case class ListVolumeRow(
    Driver: String,
    Name: String,
    Labels: Option[Map[String, String]] = None // docker loves to return null
) {
  def effectiveLabels: Map[String, String] = Labels.getOrElse(Map.empty)
}
