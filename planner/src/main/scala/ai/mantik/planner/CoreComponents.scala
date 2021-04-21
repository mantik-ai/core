package ai.mantik.planner

import ai.mantik.planner.repository.{LocalMantikRegistry, MantikArtifactRetriever}
import com.google.inject.ImplementedBy
import javax.inject.Inject

/** Encapsulates access to the core components of Mantik. */
@ImplementedBy(classOf[CoreComponents.CoreComponentsImpl])
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

object CoreComponents {
  class CoreComponentsImpl @Inject() (
      val localRegistry: LocalMantikRegistry,
      val retriever: MantikArtifactRetriever,
      val planner: Planner,
      val planExecutor: PlanExecutor
  ) extends CoreComponents
}
