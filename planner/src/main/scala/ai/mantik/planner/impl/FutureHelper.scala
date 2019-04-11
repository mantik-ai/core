package ai.mantik.planner.impl

import java.time.Clock
import java.time.temporal.ChronoUnit

import akka.actor.ActorSystem
import org.slf4j.Logger

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future, Promise, TimeoutException }
import scala.util.{ Failure, Success }

private[impl] object FutureHelper {

  /** Times a future returning function and writes to log. */
  def time[T](logger: Logger, name: String)(f: => Future[T])(implicit ec: ExecutionContext): Future[T] = {
    logger.debug(s"Executing $name")
    val t0 = System.currentTimeMillis()
    f.andThen {
      case Success(_) =>
        val t1 = System.currentTimeMillis()
        logger.debug(s"Finished executing $name, it took ${t1 - t0}ms")
      case Failure(e) =>
        val t1 = System.currentTimeMillis()
        logger.warn(s"Execution of $name failed within ${t1 - t0}ms: ${e.getMessage}")
    }
  }

  /**
   * Runs a function `f` on a given list, each after each other. Each method receives the state returned by the function before.
   * @param in input data for the function
   * @param s0 initial state
   * @param f method consuming the current state and current element of in and returning a future to the next state.
   */
  def afterEachOtherStateful[T, S](in: Iterable[T], s0: S)(f: (S, T) => Future[S])(implicit ec: ExecutionContext): Future[S] = {
    val result = Promise[S]
    def continueRunning(pending: List[T], lastResult: S): Unit = {
      pending match {
        case head :: tail =>
          f(lastResult, head).andThen {
            case Success(s) => continueRunning(tail, s)
            case Failure(e) => result.tryFailure(e)
          }
        case Nil =>
          result.trySuccess(lastResult)
      }
    }

    continueRunning(in.toList, s0)
    result.future
  }

  /**
   * Try `f` multiple times within a given timeout.
   * TODO: This function is copy and paste from executor
   */
  def tryMultipleTimes[T](timeout: FiniteDuration, tryAgainWaitDuration: FiniteDuration)(f: => Future[Option[T]])(implicit actorSystem: ActorSystem, ec: ExecutionContext): Future[T] = {
    val result = Promise[T]
    val clock = Clock.systemUTC()
    val finalTimeout = clock.instant().plus(timeout.toMillis, ChronoUnit.MILLIS)
    def tryAgain(): Unit = {
      f.andThen {
        case Success(None) =>
          if (clock.instant().isAfter(finalTimeout)) {
            result.tryFailure(new TimeoutException(s"Timeout after $timeout"))
          } else {
            actorSystem.scheduler.scheduleOnce(tryAgainWaitDuration)(tryAgain())
          }
        case Success(Some(x)) =>
          result.trySuccess(x)
        case Failure(e) =>
          result.tryFailure(e)
      }
    }
    tryAgain()
    result.future
  }
}
