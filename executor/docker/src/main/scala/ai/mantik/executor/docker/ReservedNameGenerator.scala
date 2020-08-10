package ai.mantik.executor.docker

import ai.mantik.executor.docker.ReservedNameGenerator.SingleExecutableFuture

import scala.concurrent.{ ExecutionContext, Future }

/**
 * Helper class for generating names in an efficient manner.
 * Must be thread safe, however also be the single source of truth for Mantik Containers.
 */
class ReservedNameGenerator(
    backend: ReservedNameGenerator.Backend,
    generatingSize: Int = 20
)(implicit ex: ExecutionContext) {

  private object lock

  /** The current (assumed) existing names. */
  private var existingNames: Set[String] = Set.empty

  /** Reserved names, which can't be given out. */
  private var reserved: Set[String] = Set.empty

  /** How much names can be given out without listing existing names. */
  private var countUntilRelisting: Int = 0

  /** Reserve a root name, and reserve it for the time of the user defined function. */
  def reserve[T](user: String => Future[T]): Future[T] = {
    freeAfterUser(fetchNewReservedName(), user)
  }

  /** Reserve a root name with given prefix (more expensive) */
  def reserveWithPrefix[T](prefix: String)(user: String => Future[T]): Future[T] = {
    freeAfterUser(reserveNameWithPrefix(prefix, true), user)
  }

  /** Reserve a root name with optional prefix */
  def reserveWithOptionalPrefix[T](prefix: Option[String])(user: String => Future[T]): Future[T] = {
    prefix match {
      case None         => reserve(user)
      case Some(prefix) => reserveWithPrefix(prefix)(user)
    }
  }

  private def freeAfterUser[T](r: Future[String], user: String => Future[T]): Future[T] = {
    r.flatMap { reservedName =>
      user(reservedName).andThen {
        case _ =>
          lock.synchronized {
            reserved = reserved - reservedName
            existingNames += reservedName
          }
      }
    }
  }

  private def fetchNewReservedName(): Future[String] = {
    reserveNameWithPrefix(NameGenerator.DefaultPrefix, false)
  }

  private def reserveNameWithPrefix(prefix: String, forceReload: Boolean): Future[String] = {
    if (forceReload) {
      reload().flatMap { _ =>
        reserveNameWithPrefix(prefix, false)
      }
    } else {
      lock.synchronized {
        if (countUntilRelisting <= 0) {
          return reserveNameWithPrefix(prefix, true)
        } else {
          countUntilRelisting -= 1
          val chosen = chooseName(prefix)
          Future.successful(chosen)
        }
      }
    }
  }

  private def chooseName(prefix: String): String = {
    lock.synchronized {
      val allUsed: Set[String] = existingNames ++ reserved
      val generated = backend.generate(prefix, allUsed)
      reserved += generated
      generated
    }
  }

  private val reload = new SingleExecutableFuture({
    backend.lookupAlreadyTaken().map { alreadyTaken =>
      lock.synchronized {
        countUntilRelisting = generatingSize
        existingNames = alreadyTaken
      }
      ()
    }
  })
}

object ReservedNameGenerator {

  trait Backend {
    /** Lookup already taken Names. */
    def lookupAlreadyTaken(): Future[Set[String]]

    /** Generate a new random name. */
    def generate(prefix: String, reserved: Set[String]): String
  }

  /** Helper which makes an observing function executable happening once at the same time. */
  class SingleExecutableFuture[T](f: => Future[T])(implicit ec: ExecutionContext) {
    private object lock
    private var current: Option[Future[T]] = None

    def apply(): Future[T] = {
      lock.synchronized {
        current match {
          case Some(already) => already
          case None =>
            val future = f
            current = Some(future)
            future.andThen {
              case _ =>
                lock.synchronized {
                  current = None
                }
            }
            future
        }
      }
    }
  }
}
