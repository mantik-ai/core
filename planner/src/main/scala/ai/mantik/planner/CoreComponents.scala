package ai.mantik.planner

import ai.mantik.planner.repository.{ LocalMantikRegistry, MantikArtifactRetriever }

/** Encapsulates access to the core components of Mantik. */
trait CoreComponents {

  /** The local mantik registry. */
  def localRegistry: LocalMantikRegistry

  /** Access to the Artifact Retriever */
  def retriever: MantikArtifactRetriever

  /** Access to the planner. */
  def planner: Planner

  /** Access to the plan executor. */
  def planExecutor: PlanExecutor
}
