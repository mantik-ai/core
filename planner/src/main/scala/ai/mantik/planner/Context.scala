package ai.mantik.planner

import java.nio.file.Path

import ai.mantik.planner.impl.ContextImpl
import ai.mantik.repository.{ FileRepository, MantikId, Repository }
import akka.actor.ActorSystem
import akka.stream.Materializer

/** Main Mantik Context used as main access points for Scala Apps. */
trait Context extends CoreComponents {

  /** Load a dataset from Mantik. */
  def loadDataSet(id: MantikId): DataSet

  /** Load a Transformation from Mantik. */
  def loadTransformation(id: MantikId): Algorithm

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

  /**
   * Push a local mantik file including payload to the repository
   * @return Mantik id which was used in the end.
   */
  def pushLocalMantikFile(dir: Path, id: Option[MantikId] = None): MantikId

  /** Shutdown the context. */
  def shutdown(): Unit
}

object Context {

  /** Creates a new local context. */
  def local(): Context = {
    ContextImpl.constructForLocalTesting()
  }

  /** Creates a new local context, when you already have Akka. */
  def localWithAkka()(implicit actorSystem: ActorSystem, materializer: Materializer): Context = {
    ContextImpl.constructForLocalTestingWithAkka()
  }
}