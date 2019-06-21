package ai.mantik.planner.pipelines

import ai.mantik.ds.funcational.FunctionType
import ai.mantik.elements.PipelineStep
import ai.mantik.planner.Algorithm

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
    steps.collect {
      case ResolvedPipelineStep(as: PipelineStep.AlgorithmStep, algorithm) =>
        as.algorithm -> algorithm
    }.toMap
  }
}

/** A Resolved pipeline step. */
private[planner] case class ResolvedPipelineStep(
    pipelineStep: PipelineStep,
    algorithm: Algorithm
)
