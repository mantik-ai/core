package ai.mantik.elements

import ai.mantik.ds.helper.circe.{ CirceJson, TrialDependentCodec }
import io.circe.{ Decoder, Json, ObjectEncoder }

/** A Single step within a [[PipelineDefinition]]. */
sealed trait PipelineStep {
  /** Optional description. */
  def description: Option[String]
}

object PipelineStep {

  /** Execute a algorithm. */
  case class AlgorithmStep(
      algorithm: MantikId,
      description: Option[String] = None,
      metaVariables: Option[List[MetaVariableSetting]] = None
  ) extends PipelineStep

  /** A Setting for a meta variable, used in Pipelines. */
  case class MetaVariableSetting(
      name: String,
      value: Json
  )

  /** Executes a SELECT Statement. */
  case class SelectStep(
      select: String,
      description: Option[String] = None
  ) extends PipelineStep

  implicit val metaVariableCodec: ObjectEncoder[MetaVariableSetting] with Decoder[MetaVariableSetting] = CirceJson.makeSimpleCodec[MetaVariableSetting]

  implicit val codec = new TrialDependentCodec[PipelineStep] {
    override val subTypes: Seq[SubType[_ <: PipelineStep]] = Seq(
      makeSubType[AlgorithmStep],
      makeSubType[SelectStep]
    )
  }
}
