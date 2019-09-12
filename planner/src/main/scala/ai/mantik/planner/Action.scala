package ai.mantik.planner

import ai.mantik.ds.element.Bundle
import ai.mantik.elements.NamedMantikId

/**
 * An Action is something the user requests to be executed.
 *
 * They are translated to a Plan by the [[Planner]].
 *
 * @tparam T the value returned by this action
 */
sealed trait Action[T] {
  /** Executes the action, when an implicit context is available. */
  def run()(implicit context: Context): T = context.execute(this)
}

object Action {

  /** Fetch a dataset. */
  case class FetchAction(dataSet: DataSet) extends Action[Bundle]

  /** An item should be saved */
  case class SaveAction(item: MantikItem) extends Action[Unit]

  /** An item should be pushed (indicates also saving). */
  case class PushAction(item: MantikItem) extends Action[Unit]

  /**
   * Deploy some item.
   * Returns the deployment state of the item.
   */
  case class Deploy(item: MantikItem, nameHint: Option[String] = None, ingressName: Option[String] = None) extends Action[DeploymentState]
}
