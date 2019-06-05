package ai.mantik.planner

import scala.concurrent.Future

/** Responsible for executing plans. */
trait PlanExecutor {

  /**
   * Execute a Plan.
   * @return a future to a plans result which can be casted accordingly.
   */
  def execute[T](plan: Plan[T]): Future[T]
}

object PlanExecutor {

  /** An Exception during plan execution. */
  class PlanExecutorException(msg: String) extends RuntimeException(msg)

  /** The plan to execute is invalid. */
  class InvalidPlanException(msg: String) extends PlanExecutorException(msg)
}