package ai.mantik.ds.formats.natural

import ai.mantik.ds.DataType
import io.circe.generic.semiauto._

/**
 * Describes a natural bundle.
 * The file is encoded using MsgPack
 */
case class NaturalFormatDescription(
    model: DataType,
    file: Option[String] = None
)

object NaturalFormatDescription {
  implicit val encoder = deriveEncoder[NaturalFormatDescription]
  implicit val decoder = deriveDecoder[NaturalFormatDescription]
}
