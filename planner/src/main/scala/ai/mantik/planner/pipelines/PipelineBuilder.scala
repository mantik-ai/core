package ai.mantik.planner.pipelines

import ai.mantik.ds.DataType
import ai.mantik.elements
import ai.mantik.elements.{ MantikId, Mantikfile, NamedMantikId, OptionalFunctionType, PipelineDefinition, PipelineStep }
import ai.mantik.planner.Pipeline.PipelineBuildStep
import ai.mantik.planner.{ Algorithm, DefinitionSource, MantikItem, Pipeline, Source }

private[planner] object PipelineBuilder {

  /** Build a pipeline from a mantikfile and algorithms. */
  def buildOrFailFromMantikfile(
    source: DefinitionSource,
    mantikfile: Mantikfile[PipelineDefinition],
    referenced: Map[MantikId, MantikItem]
  ): Pipeline = {
    val resolved = PipelineResolver.resolvePipeline(
      mantikfile,
      referenced
    ) match {
      case Left(error) => throw error
      case Right(ok)   => ok
    }
    new Pipeline(
      source,
      mantikfile,
      resolved
    )
  }

  /** Build a Pipeline from algorithms writing an artifical mantik file. */
  def build(algorithms: Seq[Algorithm]): Either[PipelineException, Pipeline] = {
    val inputType = algorithms.headOption.map(_.functionType.input)
    val highLevelSteps = algorithms.map { algorithm =>
      algorithm.select match {
        case Some(select) => PipelineBuildStep.SelectBuildStep(select.toSelectStatement)
        case None         => PipelineBuildStep.AlgorithmBuildStep(algorithm)
      }
    }
    build(highLevelSteps, inputType)
  }

  /**
   * Build a pipeline from high level steps.
   * @param highLevelSteps the parts of the pipeline.
   * @param definedInputType a defined input type, necessary if the first step is a SELECT.
   */
  def build(highLevelSteps: Seq[PipelineBuildStep], definedInputType: Option[DataType] = None): Either[PipelineException, Pipeline] = {
    if (highLevelSteps.isEmpty) {
      return Left(new InvalidPipelineException("Empty pipeline"))
    }

    val inputType: Option[DataType] = definedInputType.orElse {
      highLevelSteps.headOption.flatMap {
        case PipelineBuildStep.AlgorithmBuildStep(algorithm) => Some(algorithm.functionType.input)
        case _ => None
      }
    }

    val steps: Seq[(PipelineStep, Option[Algorithm])] = highLevelSteps.map {
      case PipelineBuildStep.AlgorithmBuildStep(algorithm) =>
        val id = algorithm.mantikId
        PipelineStep.AlgorithmStep(algorithm = id) -> Some(algorithm)
      case PipelineBuildStep.SelectBuildStep(statement) =>
        // A select statement
        PipelineStep.SelectStep(statement) -> None
    }

    val pipelineDefinition = elements.PipelineDefinition(
      steps = steps.map(_._1).toList,
      `type` = Some(
        OptionalFunctionType(
          input = inputType
        )
      )
    )

    val referenced = steps.collect {
      case (as: PipelineStep.AlgorithmStep, Some(algorithm)) => as.algorithm -> algorithm
    }.toMap

    val mantikFile = Mantikfile.pure(pipelineDefinition)
    PipelineResolver.resolvePipeline(
      mantikFile,
      referenced
    ).map { resolved =>
      new Pipeline(DefinitionSource.Constructed(), mantikFile, resolved)
    }
  }
}
