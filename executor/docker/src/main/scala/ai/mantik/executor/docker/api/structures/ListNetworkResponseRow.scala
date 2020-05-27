package ai.mantik.executor.docker.api.structures

import java.time.Instant

import io.circe.generic.JsonCodec

@JsonCodec
case class ListNetworkRequestFilter(
    // Note: much more fields possible
    name: Option[Vector[String]] = None,
    label: Option[Vector[String]] = None // In form label=value
)

object ListNetworkRequestFilter {
  def forLabels(labels: (String, String)*): ListNetworkRequestFilter = {
    ListNetworkRequestFilter(
      label = Some(
        labels.map { case (k, v) => s"${k}=${v}" }.toVector
      )
    )
  }
}

@JsonCodec
case class ListNetworkResponseRow(
    Name: String,
    Id: String,
    Created: Instant,
    Driver: String,
    Labels: Option[Map[String, String]] = None
) {
  def labels: Map[String, String] = Labels.getOrElse(Map.empty)
}

