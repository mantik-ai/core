package ai.mantik.executor.docker.api.structures

import io.circe.generic.JsonCodec

@JsonCodec
case class InspectNetworkResult(
    Name: String,
    Id: String,
    Driver: String,
    Labels: Option[Map[String, String]] = None
) {
  def labels: Map[String, String] = Labels.getOrElse(Map.empty)
}
