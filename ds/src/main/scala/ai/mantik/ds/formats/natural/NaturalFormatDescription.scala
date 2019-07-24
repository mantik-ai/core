package ai.mantik.ds.formats.natural

import ai.mantik.ds.DataType
import io.circe.generic.semiauto._

/**
 * Describes a natural bundle.
 * The file is encoded using MsgPack
 */
@deprecated("DS Library should not care about Mantikfile Layouts, just provide encoders.", "master")
case class NaturalFormatDescription(
    `type`: DataType,
    file: Option[String] = None
)

object NaturalFormatDescription {
  implicit val encoder = deriveEncoder[NaturalFormatDescription]
  implicit val decoder = deriveDecoder[NaturalFormatDescription]
}
