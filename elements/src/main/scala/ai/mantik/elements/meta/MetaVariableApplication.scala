package ai.mantik.elements.meta

import io.circe.{ Json, JsonObject }
import cats.implicits._

/**
 * A Transformation which applies meta variables to JSON.
 *
 * @param metaVariables the list of meta variables. may itself not contain meta variables.
 *
 * Variables have the form ${foo.bar}
 */
case class MetaVariableApplication(
    metaVariables: List[MetaVariable]
) {

  private lazy val metaVariablesByKey: Map[String, MetaVariable] = metaVariables.map { v =>
    v.name -> v
  }.toMap

  def apply(json: Json): Either[String, Json] = {
    json.fold(
      Right(Json.Null),
      b => Right(Json.fromBoolean(b)), // bool
      n => Right(Json.fromJsonNumber(n)), // number
      applyString,
      applyArray,
      x => applyObject(x).map(Json.fromJsonObject)
    )
  }

  private def applyString(s: String): Either[String, Json] = {
    if (s.startsWith("$$")) {
      // escaped
      return Right(Json.fromString(s.stripPrefix("$")))
    }
    if (s.startsWith(MetaVariableApplication.MetaVariablePrefix) &&
      s.endsWith(MetaVariableApplication.MetaVariableSuffix)) {
      val variableName = s
        .stripPrefix(MetaVariableApplication.MetaVariablePrefix)
        .stripSuffix(MetaVariableApplication.MetaVariableSuffix)

      metaVariablesByKey.get(variableName) match {
        case None        => Left(s"Variable ${variableName} not found")
        case Some(value) => Right(value.jsonValue)
      }

    } else {
      // no variable access
      Right(Json.fromString(s))
    }
  }

  private def applyArray(v: Vector[Json]): Either[String, Json] = {
    val values: Either[String, Vector[Json]] = v.map(apply).sequence
    values.map { v =>
      Json.arr(v: _*)
    }
  }

  def applyObject(j: JsonObject): Either[String, JsonObject] = {
    val valuesBefore = j.toVector
    val maybeMapped: Either[String, Vector[Json]] = valuesBefore.map {
      case (key, value) =>
        apply(value)
    }.sequence
    maybeMapped.map { mapped =>
      JsonObject.fromIterable(valuesBefore.map(_._1).zip(mapped))
    }
  }
}

object MetaVariableApplication {
  /** Prefix of a Meta Variable access. */
  val MetaVariablePrefix = "${"
  /** Suffix of a Meta Variable access. */
  val MetaVariableSuffix = "}"
}