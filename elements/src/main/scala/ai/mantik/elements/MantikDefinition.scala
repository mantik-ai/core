package ai.mantik.elements

import ai.mantik.ds.DataType
import ai.mantik.ds.funcational.FunctionType
import ai.mantik.ds.helper.circe.{ CirceJson, DiscriminatorDependentCodec }
import io.circe.{ Decoder, Encoder }

import scala.util.matching.Regex

/** A Basic Mantik Definition (algorithms, datasets, etc...) */
sealed trait MantikDefinition {
  def directory: Option[String]

  def kind: String

  def stack: String

  /** Returns referenced items. */
  def referencedItems: Seq[MantikId] = Nil
}

object MantikDefinition extends DiscriminatorDependentCodec[MantikDefinition] {
  override val subTypes = Seq(
    // Not using constants, they are not yet initialized.
    makeSubType[AlgorithmDefinition]("algorithm", isDefault = true),
    makeSubType[DataSetDefinition]("dataset"),
    makeSubType[TrainableAlgorithmDefinition]("trainable"),
    makeSubType[PipelineDefinition]("pipeline")
  )

  val AlgorithmKind = "algorithm"
  val DataSetKind = "dataset"
  val TrainableAlgorithmKind = "trainable"
  val PipelineKind = "pipeline"
}

/** An Algorithm Definition inside a Mantikfile. */
case class AlgorithmDefinition(
    // common
    directory: Option[String] = None,
    // specific
    stack: String,
    `type`: FunctionType
) extends MantikDefinition {
  def kind = MantikDefinition.AlgorithmKind
}

/** A DataSet definition inside a Mantikfile */
case class DataSetDefinition(
    // common
    directory: Option[String] = None,
    // specific
    format: String,
    `type`: DataType
) extends MantikDefinition {
  def kind = MantikDefinition.DataSetKind

  override def stack: String = format
}

case class TrainableAlgorithmDefinition(
    // common
    directory: Option[String] = None,
    // specific
    stack: String,
    trainedStack: Option[String] = None, // if not give, stack will be used
    `type`: FunctionType,
    trainingType: DataType,
    statType: DataType
) extends MantikDefinition {
  def kind = MantikDefinition.TrainableAlgorithmKind
}

/**
 * A Pipeline. A special item which refers to other algorithm items which
 * executed after each other.
 */
case class PipelineDefinition(
    // Note: the type is optional,
    `type`: Option[OptionalFunctionType] = None,
    steps: List[PipelineStep]
) extends MantikDefinition {

  // Pipelines do not carry a directory as they do not have a Bridge.
  override def directory: Option[String] = None

  override def kind: String = MantikDefinition.PipelineKind

  override def stack: String = "" // no stack needed, there is no bridge

  def inputType: Option[DataType] = `type`.flatMap(_.input)

  def outputType: Option[DataType] = `type`.flatMap(_.output)

  override def referencedItems: Seq[MantikId] = {
    steps.collect {
      case as: PipelineStep.AlgorithmStep => as.algorithm
    }
  }
}

/** A Function type where input/output are optional. */
case class OptionalFunctionType(
    input: Option[DataType] = None,
    output: Option[DataType] = None
)

object OptionalFunctionType {
  implicit val codec: Encoder[OptionalFunctionType] with Decoder[OptionalFunctionType] = CirceJson.makeSimpleCodec[OptionalFunctionType]
}
