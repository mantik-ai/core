package ai.mantik.elements

import ai.mantik.ds.helper.circe.CirceJson
import io.circe.{ Decoder, ObjectEncoder }

/**
 * Contains Meta information inside a [[MantikHeader]].
 * All fields are optional.
 *
 * All elements are directly parsed from the JSON.
 *
 * @param author author of the file, for informative use only
 * @param authorEmail email of Author
 * @param name default name of the Artifact behind the MantikHeader.
 * @param version default version of the Artifact behind the mantik header.
 * @param account default account name of the Artifact behind the mantik header.
 */
case class MantikHeaderMeta(
    author: Option[String] = None,
    authorEmail: Option[String] = None,
    name: Option[String] = None,
    version: Option[String] = None,
    account: Option[String] = None
) {

  /** Returns a MantikId for this Item, when a name is given. */
  def id: Option[NamedMantikId] = name.map { name =>
    NamedMantikId(
      name = name,
      version = version.getOrElse(NamedMantikId.DefaultVersion),
      account = account.getOrElse(NamedMantikId.DefaultAccount)
    )
  }
}

object MantikHeaderMeta {
  implicit val codec: ObjectEncoder[MantikHeaderMeta] with Decoder[MantikHeaderMeta] = CirceJson.makeSimpleCodec[MantikHeaderMeta]
}

