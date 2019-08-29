package ai.mantik.componently

import java.util.concurrent.ConcurrentLinkedDeque

import com.typesafe.scalalogging.Logger

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NonFatal

/**
 * Helper for services to maintain a coordinated shutdown.
 * Services with resources can add their shutdown hook here.
 *
 * The cleanup is done in reverse order of adding shutdown hooks.
 *
 * The Idea is stolen from Play Framework.
 */
trait Lifecycle {

  /** Add a shutdown hook. The method f is called upon shutdown and shutdown waits until f is ready. */
  def addShutdownHook(f: => Future[_]): Unit

  /** Call all shutdown hooks. */
  private[componently] def shutdown(): Future[Unit]
}

object Lifecycle {

  class SimpleLifecycle(implicit ec: ExecutionContext) extends Lifecycle {
    private val hooks = new ConcurrentLinkedDeque[() => Future[_]]()
    private val logger = Logger(getClass)

    override def addShutdownHook(f: => Future[_]): Unit = {
      hooks.push(() => f)
    }

    override def shutdown(): Future[Unit] = {
      shutdownImpl()
    }

    private def shutdownImpl(): Future[Unit] = {
      val first = hooks.poll()
      if (first == null) {
        Future.successful(())
      } else {
        first.apply().recover {
          case NonFatal(e) => logger.error("Shutdown hook failed", e)
        }.flatMap { _ =>
          shutdownImpl()
        }
      }
    }
  }
}