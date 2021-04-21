package ai.mantik.planner
import ai.mantik.ds.funcational.FunctionType
import ai.mantik.planner.pipelines.{PipelineBuilder, PipelineResolver, ResolvedPipeline}
import ai.mantik.componently.utils.EitherExtensions._
import ai.mantik.ds.DataType
import ai.mantik.ds.sql.Select
import ai.mantik.elements.{MantikHeader, PipelineDefinition}

/**
  * A Pipeline, like an algorithm but resembles the combination of multiple algorithms after each other.
  *
  * They can be stored independently in the Repository and can be deployed.
  */
case class Pipeline private[planner] (
    core: MantikItemCore[PipelineDefinition],
    private[planner] val resolved: ResolvedPipeline
) extends MantikItem
    with ApplicableMantikItem {

  def this(
      definitionSource: DefinitionSource,
      mantikHeader: MantikHeader[PipelineDefinition],
      resolved: ResolvedPipeline
  ) = {
    this(MantikItemCore(Source(definitionSource, PayloadSource.Empty), mantikHeader), resolved)
  }

  override type DefinitionType = PipelineDefinition
  override type OwnType = Pipeline

  override def functionType: FunctionType = resolved.functionType

  /** Returns the number of steps. */
  def stepCount: Int = resolved.steps.size

  override protected def withCore(updated: MantikItemCore[PipelineDefinition]): Pipeline = {
    if (updated.mantikHeader == core.mantikHeader) {
      return copy(core = updated)
    }
    // Note: this is an expensive operation here
    // as we have to re-resolve the pipeline.
    val referenced = resolved.referencedAlgorithms
    PipelineResolver.resolvePipeline(mantikHeader, referenced) match {
      case Left(error) => throw error
      case Right(resolved) =>
        Pipeline(updated, resolved)
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
      algorithm0: Algorithm,
      algorithms: Algorithm*
  ): Pipeline = {
    PipelineBuilder.build((algorithm0 +: algorithms).map(Right(_))).force
  }

  /** Extended build operation. */
  def build(
      steps: Either[Select, Algorithm]*
  ): Pipeline = {
    PipelineBuilder.build(steps).force
  }

  /** A high level Step for a Pipeline during Building. */
  sealed trait PipelineBuildStep
  object PipelineBuildStep {
    case class AlgorithmBuildStep(algorithm: Algorithm) extends PipelineBuildStep
    case class SelectBuildStep(select: String) extends PipelineBuildStep
  }

  /** Build a pipeline from a list of Pipeline steps and a possible input data type. */
  def buildFromSteps(
      steps: Seq[PipelineBuildStep],
      inputDataType: Option[DataType] = None
  ): Pipeline = {
    PipelineBuilder.build(steps, inputDataType).force
  }
}
