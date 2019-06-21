package ai.mantik.planner

import ai.mantik.planner.repository.{ FileRepository, Repository }

/** Encapsulates access to the core components of Mantik. */
trait CoreComponents {

  /** Access to files. */
  def fileRepository: FileRepository

  /** Access to the repository. */
  def repository: Repository

  /** Access to the planner. */
  def planner: Planner

  /** Access to the plan executor. */
  def planExecutor: PlanExecutor

  /** Shutdown the session. */
  def shutdown(): Unit
}
