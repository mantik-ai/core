package ai.mantik.core

import java.nio.file.Path

import ai.mantik.core.impl.ContextImpl
import ai.mantik.repository.MantikId

/** Main Mantik Context, used for accessing local simplified API. */
trait Context {

  /** Load a dataset from Mantik. */
  def loadDataSet(id: MantikId): DataSet

  /** Load a Transformation from Mantik. */
  def loadTransformation(id: MantikId): Transformation

  /** Load a Trainable Algorithm. */
  def loadTrainableAlgorithm(id: MantikId): TrainableAlgorithm

  /** Execute an Action. */
  def execute[T](action: Action[T]): T

  /** Execute multiple actions discarding the return value (convenience function) */
  def execute(action1: Action[_], action2: Action[_], moreActions: Action[_]*): Unit = {
    execute(action1)
    execute(action2)
    moreActions.foreach(execute(_))
  }

  /** Push a local mantik file including pazload to the repository */
  def pushLocalMantikFile(dir: Path): Unit

  /** Shutdown the context. */
  def shutdown(): Unit
}

object Context {

  /** Creates a new local context. */
  def local(): Context = {
    ContextImpl.constructForLocalTesting()
  }
}