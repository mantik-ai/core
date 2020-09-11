package ai.mantik.planner

import java.nio.file.Path

import ai.mantik.elements.errors.ErrorCodes
import ai.mantik.elements.{ MantikId, NamedMantikId }

import scala.reflect.ClassTag

/** Main Interface for interacting and evaluating with MantikItems */
trait PlanningContext {

  /** Load a generic Mantik Item. */
  def load(id: MantikId): MantikItem

  private def loadCasted[T <: MantikItem](id: MantikId)(implicit classTag: ClassTag[T#DefinitionType]): T = {
    val item = load(id)
    item.mantikHeader.definitionAs[T#DefinitionType] match {
      case Left(error) => throw ErrorCodes.MantikItemWrongType.toException("Wrong item type", error)
      case _           => // ok
    }
    item.asInstanceOf[T]
  }

  /** Load a dataset from Mantik. */
  def loadDataSet(id: MantikId): DataSet = loadCasted[DataSet](id)

  /** Load a Algorithm from Mantik. */
  def loadAlgorithm(id: MantikId): Algorithm = loadCasted[Algorithm](id)

  /** Load a Trainable Algorithm. */
  def loadTrainableAlgorithm(id: MantikId): TrainableAlgorithm = loadCasted[TrainableAlgorithm](id)

  /** Load a Pipeline. */
  def loadPipeline(id: MantikId): Pipeline = loadCasted[Pipeline](id)

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
   * Push a local mantik item including payload to the repository.
   * There must be a MantikItem present.
   * @return Mantik id which was used in the end.
   */
  def pushLocalMantikItem(dir: Path, id: Option[NamedMantikId] = None): MantikId

  /** Returns the internal state of a MantikItem */
  def state(item: MantikItem): MantikItemState
}
