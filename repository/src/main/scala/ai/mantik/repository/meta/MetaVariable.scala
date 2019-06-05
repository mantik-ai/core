package ai.mantik.repository.meta

import ai.mantik.ds.element.{ SingleElementBundle, TabularBundle }
import ai.mantik.ds.formats.json.JsonFormat
import io.circe.Decoder.Result
import io.circe.{ Decoder, DecodingFailure, Encoder, HCursor, Json, JsonObject }

/**
 * A Meta variable used in [[MetaVariableApplication]].
 * @param name name of the meta variable
 * @param value value of the meta variable.
 * @param fix if fix, a meta variable may not be changed anymore.
 */
case class MetaVariable(
    name: String,
    value: SingleElementBundle,
    fix: Boolean = false
) {
  /** The json encoded value. */
  lazy val jsonValue: Json = JsonFormat.encodeObjectValue(value)
}

object MetaVariable {

  implicit val encoder: Encoder[MetaVariable] = new Encoder[MetaVariable] {
    override def apply(a: MetaVariable): Json = {
      Json.obj(
        "name" -> Json.fromString(a.name),
        "fix" -> Json.fromBoolean(a.fix)
      ).deepMerge(
          Json.fromJsonObject(JsonFormat.encodeObject(a.value))
        )
    }
  }

  implicit val decoder: Decoder[MetaVariable] = new Decoder[MetaVariable] {
    override def apply(c: HCursor): Result[MetaVariable] = {
      for {
        value <- JsonFormat.decodeJson(c.value)
        singleValue <- value match {
          case s: SingleElementBundle => Right(s)
          case m: TabularBundle       => Left(DecodingFailure("Only single element bundles supported", Nil))
        }
        name <- c.get[String]("name")
        fix <- c.getOrElse[Boolean]("fix")(false)
      } yield {
        MetaVariable(
          name,
          singleValue,
          fix
        )
      }
    }
  }
}
