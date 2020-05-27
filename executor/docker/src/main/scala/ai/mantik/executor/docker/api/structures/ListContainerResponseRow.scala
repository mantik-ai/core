package ai.mantik.executor.docker.api.structures

import io.circe.generic.JsonCodec

@JsonCodec
case class ListContainerRequestFilter(
    label: Vector[String]
)

object ListContainerRequestFilter {
  /**
   * Build a filter for a given label key and value.
   * For more information see Docker API specification.
   */
  def forLabelKeyValue(keyValues: (String, String)*): ListContainerRequestFilter = {
    val labels = keyValues.map {
      case (key, value) =>
        s"$key=$value"
    }.toVector
    ListContainerRequestFilter(
      label = labels
    )
  }
}

/** Single row on a list container field. */
@JsonCodec
case class ListContainerResponseRow(
    Id: String,
    Image: String,
    Command: Option[String],
    Names: Vector[String] = Vector.empty,
    Labels: Map[String, String] = Map.empty,
    State: String,
    // Human readable status code
    Status: Option[String] = None
)

