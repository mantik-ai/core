package ai.mantik.core

import ai.mantik.ds.natural.NaturalBundle
import ai.mantik.repository.MantikId

/**
 * A Action is something which can be executed
 * @tparam T the value returned by this action
 */
sealed trait Action[T]

object Action {

  /** Fetch a dataset. */
  case class FetchAction(dataSet: DataSet) extends Action[NaturalBundle]

  /** Algorithm is trained. */
  case class TrainAlgorithmAction(trainableAlgorithm: TrainableAlgorithm, trainData: DataSet, validationData: Option[DataSet]) extends Action[Unit]

  /** Something is going to be saved. */
  case class SaveAction(item: MantikItem, location: MantikId) extends Action[Unit]
}
