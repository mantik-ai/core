package ai.mantik.planner

import java.nio.file.Path

import ai.mantik.elements.{ MantikId, NamedMantikId }

/** Main Mantik Context used as main access points for Scala Apps. */
trait Context extends CoreComponents {

  /** Load a dataset from Mantik. */
  def loadDataSet(id: MantikId): DataSet

  /** Load a Algorithm from Mantik. */
  def loadAlgorithm(id: MantikId): Algorithm

  /** Load a Trainable Algorithm. */
  def loadTrainableAlgorithm(id: MantikId): TrainableAlgorithm

  /** Load a Pipeline. */
  def loadPipeline(id: MantikId): Pipeline

  /** Pulls an item from registry. */
  def pull(id: MantikId): MantikItem

  /** Execute an Action. */
  def execute[T](action: Action[T]): T

  /** Execute multiple actions discarding the return value (convenience function) */
  def execute(action1: Action[_], action2: Action[_], moreActions: Action[_]*): Unit = {
    execute(action1)
    execute(action2)
    moreActions.foreach(execute(_))
  }

  /**
   * Push a local mantik file including payload to the repository
   * @return Mantik id which was used in the end.
   */
  def pushLocalMantikFile(dir: Path, id: Option[NamedMantikId] = None): MantikId
}
