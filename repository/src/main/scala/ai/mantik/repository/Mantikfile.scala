package ai.mantik.repository

import io.circe._
import io.circe.syntax._
import io.circe.yaml.syntax._
import io.circe.yaml.{ parser => YamlParser }
import scala.reflect.ClassTag

/**
 * A Mantikfile. Contains one Mantik Definition.
 */
case class Mantikfile[T <: MantikDefinition](
    definition: T,
    json: Json
) {

  def algorithm: Option[AlgorithmDefinition] = definition match {
    case a: AlgorithmDefinition => Some(a)
    case _                      => None
  }

  def dataSet: Option[DataSetDefinition] = definition match {
    case d: DataSetDefinition => Some(d)
    case _                    => None
  }

  def cast[T <: MantikDefinition](implicit ct: ClassTag[T]): Either[io.circe.Error, Mantikfile[T]] = {
    definition match {
      case x: T => Right(Mantikfile[T](x, json))
      case _    => Left(DecodingFailure(s"Expected ${ct.runtimeClass.getSimpleName}, got ${definition.getClass.getSimpleName}", Nil))
    }
  }

  /** Returns Yaml code  */
  def toYaml: String = json.asYaml.spaces2

  /** Returns a set of violations. */
  lazy val violations: Seq[String] = definition.violations
}

object Mantikfile {

  /** Generates a Mantikfile from pure Definition, automatically serializing to JSON. */
  def pure(definition: MantikDefinition): Mantikfile[MantikDefinition] = {
    Mantikfile(
      definition, definition.asJson
    )
  }

  /** Parse a YAML File. */
  def fromYaml(content: String): Either[io.circe.Error, Mantikfile[MantikDefinition]] = {
    YamlParser.parse(content) match {
      case Left(error) => Left(error)
      case Right(json) => parseSingleDefinition(json)
    }
  }

  /** Parse a YAML File, expecting a single definition only. */
  def fromSingleYamlWithType[T <: MantikDefinition](content: String)(implicit classTag: ClassTag[T]): Either[io.circe.Error, Mantikfile[T]] = {
    for {
      json <- parser.parse(content)
      parsed <- parseSingleDefinition(json)
      casted <- parsed.cast[T]
    } yield casted
  }

  def parseSingleDefinition(json: Json): Either[io.circe.Error, Mantikfile[MantikDefinition]] = {
    json.as[MantikDefinition].map { v =>
      Mantikfile(v, json)
    }
  }

}
