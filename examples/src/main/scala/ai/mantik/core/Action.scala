package ai.mantik.core

import ai.mantik.ds.element.Bundle
import ai.mantik.repository.{ MantikDefinition, MantikId }

/**
 * A Action is something which can be executed
 * @tparam T the value returned by this action
 */
sealed trait Action[T]

object Action {

  /** Fetch a dataset. */
  case class FetchAction(dataSet: DataSet) extends Action[Bundle]

  /** Something is going to be saved. */
  case class SaveAction(item: MantikItem, id: MantikId) extends Action[Unit]
}
