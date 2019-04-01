package ai.mantik.ds.formats.natural

import ai.mantik.ds.DataType
import ai.mantik.ds.helper.circe.CirceJson

/** The header in a Natural data stream. */
case class Header(
    format: DataType
)

object Header {
  implicit val codec = CirceJson.makeSimpleCodec[Header]
}