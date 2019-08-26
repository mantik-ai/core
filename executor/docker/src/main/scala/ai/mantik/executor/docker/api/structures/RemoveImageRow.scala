package ai.mantik.executor.docker.api.structures

import io.circe.generic.JsonCodec

/** Result row in remove image response */
@JsonCodec
case class RemoveImageRow(
    Untagged: Option[String],
    Deleted: Option[String]
)
