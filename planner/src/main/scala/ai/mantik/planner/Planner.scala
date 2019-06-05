package ai.mantik.planner

import ai.mantik.planner.bridge.Bridges
import ai.mantik.planner.impl.PlannerImpl
import com.typesafe.config.Config

/** A Planner converts an [[Action]] into an executable [[Plan]]. */
trait Planner {

  /** Convert a set of action into a plan. */
  def convert[T](action: Action[T]): Plan[T]
}

object Planner {

  /** Create a planner instance. */
  def create(config: Config): Planner = new PlannerImpl(Bridges.loadFromConfig(config))

  class PlannerException(msg: String) extends RuntimeException(msg)

  class FormatNotSupportedException(msg: String) extends PlannerException(msg)

  class AlgorithmStackNotSupportedException(msg: String) extends PlannerException(msg)

  class NotAvailableException(msg: String) extends PlannerException(msg)

  class InconsistencyException(msg: String) extends PlannerException(msg)
}