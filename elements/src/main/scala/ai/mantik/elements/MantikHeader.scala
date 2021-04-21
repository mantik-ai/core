package ai.mantik.elements

import ai.mantik.ds.element.SingleElementBundle
import ai.mantik.elements.errors.{ErrorCodes, InvalidMantikHeaderException, MantikException}
import ai.mantik.elements.meta.{MetaJson, MetaVariableException}
import io.circe.Decoder.Result
import io.circe.{Decoder, DecodingFailure, Error, HCursor, Json, JsonObject, ObjectEncoder}
import io.circe.syntax._
import io.circe.yaml.syntax._
import io.circe.yaml.{Printer, parser => YamlParser}

import scala.reflect.ClassTag

/**
  * A MantikHeader file. Contains one Mantik Definition together with it's JSON representation.
  */
case class MantikHeader[T <: MantikDefinition](
    definition: T,
    metaJson: MetaJson,
    header: MantikHeaderMeta
) {

  /** Returns the definition, if applicable. */
  def definitionAs[T <: MantikDefinition](implicit ct: ClassTag[T]): Either[MantikException, T] =
    cast[T].right.map(_.definition)

  def cast[T <: MantikDefinition](implicit ct: ClassTag[T]): Either[MantikException, MantikHeader[T]] = {
    definition match {
      case x: T => Right(MantikHeader[T](x, metaJson, header))
      case _ =>
        Left(
          ErrorCodes.MantikItemWrongType.toException(
            s"Expected ${ct.runtimeClass.getSimpleName}, got ${definition.getClass.getSimpleName}"
          )
        )
    }
  }

  /** Returns Yaml code */
  def toYaml: String = {
    Printer(preserveOrder = true).pretty(toJsonValue)
  }

  /** Returns Json code. */
  def toJson: String = toJsonValue.spaces2

  /** Returns the json value (before converting to string) */
  def toJsonValue: Json = metaJson.asJson

  override def toString: String = {
    val bridge = definition match {
      case b: MantikDefinitionWithBridge => Some(b.bridge)
      case _                             => None
    }
    s"MantikHeader(${definition.kind},bridge=${bridge},name=${header.name})"
  }

  /**
    * Update Meta Variable Values
    * @throws MetaVariableException see [[MetaJson.withMetaValues]].
    */
  def withMetaValues(values: (String, SingleElementBundle)*): MantikHeader[T] = {
    val updatedJson = metaJson.withMetaValues(values: _*)
    val resultCandidate = for {
      parsed <- MantikHeader.parseMetaJson(updatedJson)
      castedDefinition = parsed.definition.asInstanceOf[T]
    } yield MantikHeader(castedDefinition, parsed.metaJson, parsed.header)
    // parsing errors should not happen much (but is possible, types can go invalid)
    resultCandidate match {
      case Left(error)  => throw new MetaVariableException("Could not reparse with changed meta values ", error)
      case Right(value) => value
    }
  }

  /** Return violations (note: cannot spot bridge-related violations) */
  def violations: Seq[String] = {
    val mantikId = header.id
    mantikId.map(_.violations).getOrElse(Nil)
  }
}

object MantikHeader {

  /** Generates a MantikHeader from pure Definition, automatically serializing to JSON. */
  def pure[T <: MantikDefinition](definition: T): MantikHeader[T] = {
    MantikHeader(
      definition,
      MetaJson(
        metaVariables = Nil,
        missingMetaVariables = true,
        sourceJson = (definition: MantikDefinition).asJsonObject
      ),
      header = MantikHeaderMeta()
    )
  }

  /** Parse a YAML File. */
  def fromYaml(content: String): Either[InvalidMantikHeaderException, MantikHeader[MantikDefinition]] = {
    fromYamlWithoutCheck(content).flatMap { mantikHeader =>
      mantikHeader.violations match {
        case s if s.isEmpty => Right(mantikHeader)
        case violations     => Left(new InvalidMantikHeaderException(s"Invalid MantikHeader: ${violations.mkString(",")}"))
      }
    }
  }

  /** Parse a YAML file without further checking of violations. */
  def fromYamlWithoutCheck(content: String): Either[InvalidMantikHeaderException, MantikHeader[MantikDefinition]] = {
    YamlParser.parse(content) match {
      case Left(error) => Left(InvalidMantikHeaderException.wrap(error))
      case Right(json) => parseSingleDefinition(json)
    }
  }

  /** Parse a YAML File, expecting a single definition only. */
  def fromYamlWithType[T <: MantikDefinition](
      content: String
  )(implicit classTag: ClassTag[T]): Either[MantikException, MantikHeader[T]] = {
    for {
      parsed <- fromYaml(content)
      casted <- parsed.cast[T]
    } yield casted
  }

  def parseSingleDefinition(json: Json): Either[InvalidMantikHeaderException, MantikHeader[MantikDefinition]] = {
    json.as[MetaJson].flatMap(parseMetaJson).left.map {
      InvalidMantikHeaderException.wrap
    }
  }

  def parseMetaJson(metaJson: MetaJson): Either[InvalidMantikHeaderException, MantikHeader[MantikDefinition]] = {
    (for {
      applied <- metaJson.appliedJson.left.map { error => DecodingFailure(error, Nil) }
      definition <- applied.as[MantikDefinition]
      header <- applied.as[MantikHeaderMeta]
    } yield MantikHeader(definition, metaJson, header)).left.map(InvalidMantikHeaderException.wrap)
  }

  /** Generate the mantikHeader for a trained algorithm out of a trainable algorithm definition. */
  def generateTrainedMantikHeader(
      trainable: MantikHeader[TrainableAlgorithmDefinition]
  ): Either[MantikException, MantikHeader[AlgorithmDefinition]] = {
    val trainedBridge = trainable.definition.trainedBridge.getOrElse(
      trainable.definition.bridge // if no override given, use the same bridge
    )
    val updatedJsonObject = trainable.metaJson.withFixedVariables.copy(
      sourceJson = trainable.metaJson.sourceJson
        .remove("name")
        .remove("version")
        .remove("trainedBridge")
        .add("bridge", trainedBridge.asJson)
        .add("kind", Json.fromString(MantikDefinition.AlgorithmKind))
    )
    MantikHeader.parseMetaJson(updatedJsonObject).flatMap(_.cast[AlgorithmDefinition])
  }

  /** Encodes a MantikHeader to it's json value. */
  implicit def encoder[T <: MantikDefinition]: ObjectEncoder[MantikHeader[T]] = new ObjectEncoder[MantikHeader[T]] {
    override def encodeObject(a: MantikHeader[T]): JsonObject = a.metaJson.asJsonObject
  }

  /** Decodes a MantikHeader from JSON. */
  implicit def decoder[T <: MantikDefinition: ClassTag]: Decoder[MantikHeader[T]] = new Decoder[MantikHeader[T]] {
    override def apply(c: HCursor): Result[MantikHeader[T]] = {
      val result = for {
        metaJson <- c.as[MetaJson]
        parsed <- MantikHeader.parseMetaJson(metaJson)
        casted <- parsed.cast[T]
      } yield casted
      result match {
        case Left(e: DecodingFailure) => Left(e)
        case Left(other)              => new Left(DecodingFailure(other.getMessage, Nil))
        case Right(ok)                => Right(ok)
      }
    }
  }
}
