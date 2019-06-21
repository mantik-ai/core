package ai.mantik.elements

import ai.mantik.ds.element.SingleElementBundle
import ai.mantik.elements.meta.{ MetaJson, MetaVariableException }
import io.circe.{ DecodingFailure, Json, Error }
import io.circe.syntax._
import io.circe.yaml.syntax._
import io.circe.yaml.{ parser => YamlParser }

import scala.reflect.ClassTag

/**
 * A Mantikfile. Contains one Mantik Definition together with it's JSON representation.
 */
case class Mantikfile[T <: MantikDefinition](
    definition: T,
    metaJson: MetaJson,
    header: MantikHeader
) {

  /** Returns the definition, if applicable. */
  def definitionAs[T <: MantikDefinition](implicit ct: ClassTag[T]): Either[io.circe.Error, T] = cast[T].right.map(_.definition)

  def cast[T <: MantikDefinition](implicit ct: ClassTag[T]): Either[io.circe.Error, Mantikfile[T]] = {
    definition match {
      case x: T => Right(Mantikfile[T](x, metaJson, header))
      case _    => Left(DecodingFailure(s"Expected ${ct.runtimeClass.getSimpleName}, got ${definition.getClass.getSimpleName}", Nil))
    }
  }

  /** Returns Yaml code  */
  def toYaml: String = toJsonValue.asYaml.spaces2

  /** Returns Json code. */
  def toJson: String = toJsonValue.spaces2

  /** Returns the json value (before converting to string) */
  def toJsonValue: Json = metaJson.asJson

  override def toString: String = {
    s"Mantikfile(${definition.kind},stack=${definition.stack},name=${header.name})"
  }

  /**
   * Update Meta Variable Values
   * @throws MetaVariableException see [[MetaJson.withMetaValues]].
   */
  def withMetaValues(values: (String, SingleElementBundle)*): Mantikfile[T] = {
    val updatedJson = metaJson.withMetaValues(values: _*)
    val resultCandidate = for {
      parsed <- Mantikfile.parseMetaJson(updatedJson)
      castedDefinition = parsed.definition.asInstanceOf[T]
    } yield Mantikfile(castedDefinition, parsed.metaJson, parsed.header)
    // parsing errors should not happen much (but is possible, types can go invalid)
    resultCandidate match {
      case Left(error)  => throw new MetaVariableException("Could not reparse with changed meta values ", error)
      case Right(value) => value
    }
  }
}

object Mantikfile {

  /** Generates a Mantikfile from pure Definition, automatically serializing to JSON. */
  def pure[T <: MantikDefinition](definition: T): Mantikfile[T] = {
    Mantikfile(
      definition,
      MetaJson(
        metaVariables = Nil,
        missingMetaVariables = true,
        sourceJson = (definition: MantikDefinition).asJsonObject
      ),
      header = MantikHeader()
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
  def fromYamlWithType[T <: MantikDefinition](content: String)(implicit classTag: ClassTag[T]): Either[io.circe.Error, Mantikfile[T]] = {
    for {
      parsed <- fromYaml(content)
      casted <- parsed.cast[T]
    } yield casted
  }

  def parseSingleDefinition(json: Json): Either[io.circe.Error, Mantikfile[MantikDefinition]] = {
    json.as[MetaJson].flatMap(parseMetaJson)
  }

  def parseMetaJson(metaJson: MetaJson): Either[io.circe.Error, Mantikfile[MantikDefinition]] = {
    for {
      applied <- metaJson.appliedJson.left.map { error => DecodingFailure(error, Nil) }
      definition <- applied.as[MantikDefinition]
      header <- applied.as[MantikHeader]
    } yield Mantikfile(definition, metaJson, header)
  }

  /** Generate the mantikfile for a trained algorithm out of a trainable algorithm definition. */
  def generateTrainedMantikfile(trainable: Mantikfile[TrainableAlgorithmDefinition]): Either[Error, Mantikfile[AlgorithmDefinition]] = {
    val trainedStack = trainable.definition.trainedStack.getOrElse(
      trainable.definition.stack // if no override given, use the same stack
    )
    val updatedJsonObject = trainable.metaJson.withFixedVariables.copy(
      sourceJson = trainable.metaJson.sourceJson
        .remove("name")
        .remove("version")
        .remove("trainedStack")
        .add("stack", Json.fromString(trainedStack))
        .add("kind", Json.fromString(MantikDefinition.AlgorithmKind))
    )
    Mantikfile.parseMetaJson(updatedJsonObject).flatMap(_.cast[AlgorithmDefinition])
  }

}
