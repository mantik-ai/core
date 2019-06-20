package ai.mantik.repository

import ai.mantik.ds.helper.circe.CirceJson
import io.circe.{ Decoder, ObjectEncoder }

/**
 * A Header which can be part of a Mantikfile.
 * The header is completely optional.
 *
 * All Items inside the header are directly parsed from the JSON.
 * @param author author of the file, for informative use only
 * @param authorEmail email of Author
 * @param name default name of the Artifact behind the Mantikfile.
 * @param version default version of the Artifact behind the mantik file.
 */
case class MantikHeader(
    author: Option[String] = None,
    authorEmail: Option[String] = None,
    name: Option[String] = None,
    version: Option[String] = None
) {

  /** Returns a MantikId for this Item, when a name is given. */
  def id: Option[MantikId] = name.map { name =>
    MantikId(name, version.getOrElse(MantikId.DefaultVersion))
  }
}

object MantikHeader {
  implicit val codec: ObjectEncoder[MantikHeader] with Decoder[MantikHeader] = CirceJson.makeSimpleCodec[MantikHeader]
}

