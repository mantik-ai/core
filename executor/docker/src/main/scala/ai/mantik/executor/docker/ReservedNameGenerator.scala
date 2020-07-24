package ai.mantik.executor.docker

import scala.collection.immutable.Queue
import scala.concurrent.{ ExecutionContext, Future }

/**
 * Helper class for generating names in an efficient manner.
 * Must be thread safe
 */
class ReservedNameGenerator(
    backend: ReservedNameGenerator.BunchGenerator,
    bufSize: Int = 20
)(implicit ex: ExecutionContext) {

  private object lock
  private var nextNames: Queue[String] = Queue.empty
  private var nextNameUpdate: Option[Future[Unit]] = None
  private var reserved: Set[String] = Set.empty

  /** Generate a new root name. */
  def generateRootName(): Future[String] = {
    reserve { name =>
      Future.successful(name)
    }
  }

  /** Reserve a root name, and reserve it for the time of the user defined function. */
  def reserve[T](user: String => Future[T]): Future[T] = {
    val reservedNextName: Option[String] = lock.synchronized {
      nextNames.dequeueOption match {
        case None =>
          None
        case Some((nextName, nextQueue)) =>
          reserved = reserved + nextName
          nextNames = nextQueue
          Some(nextName)
      }
    }

    reservedNextName match {
      case None => withIncreasedBuffer().flatMap { _ => reserve(user) }
      case Some(name) =>
        user(name).andThen {
          case _ =>
            lock.synchronized {
              reserved = reserved - name
            }
        }
    }
  }

  private def withIncreasedBuffer(): Future[Unit] = {
    lock.synchronized {
      nextNameUpdate match {
        // do not give out failed futures multiple times
        case Some(alreadyHappening) if !alreadyHappening.value.exists(_.isFailure) =>
          alreadyHappening
        case None =>
          val future = executeBufferIncreaseLocked()
          nextNameUpdate = Some(future)
          future
      }
    }
  }

  private def executeBufferIncreaseLocked(): Future[Unit] = {
    val allReserved: Set[String] = reserved ++ nextNames
    backend.generate(bufSize, allReserved).map { results =>
      lock.synchronized {
        nextNames ++= results
        nextNameUpdate = None
      }
      ()
    }
  }
}

object ReservedNameGenerator {
  /** Generate bunches of new Names for [[ReservedNameGenerator]] */
  trait BunchGenerator {
    def generate(count: Int, reserved: Set[String]): Future[Set[String]]
  }

  /** Generate bunches of new names for [[ReservedNameGenerator]] by looking up all names first. */
  trait PrelistedBunchGenerator extends BunchGenerator {
    protected def executionContext: ExecutionContext

    override def generate(count: Int, reserved: Set[String]): Future[Set[String]] = {
      implicit val ec = executionContext
      lookupAlreadyTaken().map { alreadyTaken =>
        var fullSet = reserved ++ alreadyTaken
        val resultSetBuilder = Set.newBuilder[String]
        for (_ <- 0 until count) {
          val next = generateSingle(fullSet)
          resultSetBuilder += next
          fullSet += next
        }
        resultSetBuilder.result()
      }
    }

    protected def lookupAlreadyTaken(): Future[Set[String]]

    protected def generateSingle(taken: Set[String]): String
  }
}
