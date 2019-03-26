package ai.mantik.core

import scala.concurrent.Future

/** A Planner converts Actions into executable Jobs. */
trait Planner {

  /** Convert a set of action into a job. */
  def convert[T](action: Action[T]): Future[Plan]
}

object Planner {
  class PlannerException(msg: String) extends RuntimeException(msg)

  class FormatNotSupportedException(msg: String) extends PlannerException(msg)

  class AlgorithmStackNotSupportedException(msg: String) extends PlannerException(msg)

  class NotAvailableException(msg: String) extends PlannerException(msg)

  class InconsistencyException(msg: String) extends PlannerException(msg)
}