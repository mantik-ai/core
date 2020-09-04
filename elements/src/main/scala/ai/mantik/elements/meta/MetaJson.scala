package ai.mantik.elements.meta

import ai.mantik.ds.element.SingleElementBundle
import io.circe.Decoder.Result
import io.circe.{ Decoder, DecodingFailure, Encoder, HCursor, Json, JsonObject, ObjectEncoder }
import io.circe.syntax._

/** Exception thrown if a Meta Variable changing doesn't work. */
class MetaVariableException(msg: String, err: Throwable = null) extends RuntimeException(msg, err)

/**
 * Meta JSON combines a JSON object with a list of meta variables.
 * @param sourceJson source (except metaVariables block)
 * @param metaVariables embedded meta variables
 * @param missingMetaVariables if true, meta variables was never in JSON and can be omitted during serialisation.
 */
case class MetaJson(
    sourceJson: JsonObject,
    metaVariables: List[MetaVariable],
    missingMetaVariables: Boolean = false
) {

  /** Applies meta variables to inner json. */
  def applied: Either[String, JsonObject] = MetaVariableApplication(metaVariables).applyObject(sourceJson)

  /** Returns the applied code as circe json. */
  def appliedJson: Either[String, Json] = applied.map(Json.fromJsonObject)

  /** Returns the value of a meta variable, if found. */
  def metaVariable(name: String): Option[MetaVariable] = metaVariables.find(_.name == name)

  /** Returns a copy where all variables are fixed. */
  def withFixedVariables: MetaJson = {
    copy(
      metaVariables = metaVariables.map { variable =>
        variable.copy(fix = true)
      }
    )
  }

  /**
   * Override meta variables.
   * @throws MetaVariableException if a value is missing or of wrong type or not changeable.
   */
  def withMetaValues(values: (String, SingleElementBundle)*): MetaJson = {
    val overrideMap = values.toMap
    val newValues = metaVariables.map {
      case m: MetaVariable if overrideMap.contains(m.name) =>
        if (m.fix) {
          throw new MetaVariableException(s"Variable ${m.name} is fix and can not be changed")
        }
        val o = overrideMap(m.name)
        val casted = o.cast(m.value.model, allowLoosing = true) match {
          case Left(error) => throw new MetaVariableException(s"Invalid type, expected ${m.value.model}, got ${o.model}, cast failed ${error}")
          case Right(v)    => v
        }
        m.copy(
          value = casted
        )
      case other => other
    }
    val missing = overrideMap.keys.toList.diff(metaVariables.map(_.name))
    if (missing.nonEmpty) {
      throw new MetaVariableException(s"Metavariables ${missing} not found")
    }
    copy(
      metaVariables = newValues
    )
  }
}

object MetaJson {

  /**
   * Generate a Meta Json instance when you are sure there are no meta variables.
   * @throws IllegalArgumentException if there are meta variables.
   */
  def withoutMetaVariables(in: JsonObject): MetaJson = {
    require(in(MetaVariablesKey).isEmpty, "No meta variables allowed")
    MetaJson(
      metaVariables = Nil,
      sourceJson = in,
      missingMetaVariables = true
    )
  }

  implicit val decoder: Decoder[MetaJson] = new Decoder[MetaJson] {
    override def apply(c: HCursor): Result[MetaJson] = {
      c.value.asObject match {
        case None => Left(DecodingFailure("Expected object", Nil))
        case Some(o) =>
          val maybeMetaVariables = o(MetaVariablesKey) match {
            case None =>
              // meta variables do not have to be present.
              return Right(withoutMetaVariables(o))
            case Some(metaBlock) =>
              metaBlock.as[List[MetaVariable]]
          }
          maybeMetaVariables.map { variables =>
            val rest = o.filterKeys(_ != MetaVariablesKey)
            MetaJson(rest, variables)
          }
      }
    }
  }

  implicit val encoder: ObjectEncoder[MetaJson] = new ObjectEncoder[MetaJson] {

    override def encodeObject(a: MetaJson): JsonObject = {
      val skipMeta = a.missingMetaVariables && a.metaVariables.isEmpty
      if (skipMeta) {
        return a.sourceJson
      }
      val variablesJson = a.metaVariables.asJson
      Json.obj(
        MetaVariablesKey -> variablesJson
      ).deepMerge(
          Json.fromJsonObject(a.sourceJson)
        ).asObject.get
    }
  }

  private val MetaVariablesKey = "metaVariables"

}