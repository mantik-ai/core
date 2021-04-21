package ai.mantik.planner.pipelines

import ai.mantik.ds.funcational.FunctionType
import ai.mantik.ds.helper.circe.DiscriminatorDependentCodec
import ai.mantik.ds.sql.Select
import ai.mantik.elements.PipelineStep
import ai.mantik.planner.Algorithm
import ai.mantik.planner.impl.MantikItemCodec
import io.circe.{Decoder, ObjectEncoder}

/**
  * A Resolved pipeline.
  * All pipeline steps are matched to algorithms.
  */
private[planner] case class ResolvedPipeline(
    steps: List[ResolvedPipelineStep],
    functionType: FunctionType
) {

  /** Build a Map of Referenced Algorithms. */
  private[planner] def referencedAlgorithms: PipelineResolver.ReferencedAlgorithms = {
    steps.collect { case as: ResolvedPipelineStep.AlgorithmStep =>
      as.algorithm.mantikId -> as.algorithm
    }.toMap
  }
}

private[planner] sealed trait ResolvedPipelineStep {

  /** The function type of this pipeline step. */
  def functionType: FunctionType
}

private[planner] object ResolvedPipelineStep {

  case class AlgorithmStep(algorithm: Algorithm) extends ResolvedPipelineStep {
    override def functionType: FunctionType = algorithm.functionType
  }

  case class SelectStep(select: Select) extends ResolvedPipelineStep {
    override def functionType: FunctionType = FunctionType(select.inputTabularType, select.resultingTabularType)
  }
}
