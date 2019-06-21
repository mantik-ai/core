package ai.mantik.planner
import ai.mantik.ds.funcational.FunctionType
import ai.mantik.planner.pipelines.{ PipelineBuilder, PipelineResolver, ResolvedPipeline }
import EitherUtils._
import ai.mantik.ds.DataType
import ai.mantik.elements.{ Mantikfile, PipelineDefinition }

/**
 * A Pipeline, like an algorithm but resembles the combination of multiple algorithms after each other.
 *
 * They can be stored independently in the Repository and can be deployed.
 */
class Pipeline private[planner] (
    private[planner] val definitionSource: DefinitionSource,
    private[planner] val mantikfile: Mantikfile[PipelineDefinition],
    private[planner] val resolved: ResolvedPipeline
) extends MantikItem with ApplicableMantikItem {

  override type DefinitionType = PipelineDefinition
  override type OwnType = Pipeline

  /** Pipelines do not have a source. */
  override val payloadSource: PayloadSource = PayloadSource.Empty

  override def source: Source = Source(definitionSource, payloadSource)

  override def functionType: FunctionType = resolved.functionType

  /** Returns the number of steps. */
  def stepCount: Int = resolved.steps.size

  override protected def withMantikfile(mantikfile: Mantikfile[PipelineDefinition]): Pipeline = {
    // Note: this is an expensive operation here
    // as we have to re-resolve the pipeline.
    val referenced = resolved.referencedAlgorithms
    PipelineResolver.resolvePipeline(mantikfile, referenced) match {
      case Left(error) => throw error
      case Right(resolved) => new Pipeline(
        DefinitionSource.Derived(definitionSource), mantikfile, resolved
      )
    }
  }
}

object Pipeline {

  /**
   * Build a pipeline from a list of algorithms.
   * This will result in artificial child mantik ids.
   * @throws IllegalArgumentException if data types do not match.
   */
  def build(
    algorithms: Algorithm*
  ): Pipeline = {
    PipelineBuilder.build(algorithms).force
  }

  /** A high level Step for a Pipeline during Building. */
  sealed trait PipelineBuildStep
  object PipelineBuildStep {
    case class AlgorithmBuildStep(algorithm: Algorithm) extends PipelineBuildStep
    case class SelectBuildStep(select: String) extends PipelineBuildStep
  }

  /** Build a pipeline from a list of Pipeline steps and a possible input data type. */
  def buildFromSteps(
    steps: Seq[PipelineBuildStep], inputDataType: Option[DataType] = None
  ): Pipeline = {
    PipelineBuilder.build(steps, inputDataType).force
  }
}