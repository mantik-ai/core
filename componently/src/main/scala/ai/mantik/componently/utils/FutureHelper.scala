/*
 * This file is part of the Mantik Project.
 * Copyright (c) 2020-2021 Mantik UG (Haftungsbeschränkt)
 * Authors: See AUTHORS file
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.
 *
 * Additionally, the following linking exception is granted:
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with other code, such other code is not for that reason
 * alone subject to any of the requirements of the GNU Affero GPL
 * version 3.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license.
 */
package ai.mantik.componently.utils

import java.time.Clock
import java.time.temporal.ChronoUnit

import ai.mantik.componently.AkkaRuntime
import akka.actor.ActorSystem
import com.typesafe.scalalogging.Logger

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise, TimeoutException}
import scala.util.{Failure, Success}

object FutureHelper {

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
    * Let a future with [[scala.concurrent.TimeoutException]] if it takes too long.
    * Note: the inner code of the future will still run, as they are not cancellable.
    * @param operationName an operation name, which will be displayed in the exception.
    * @return the future which will fail with timeout.
    */
  def addTimeout[T](future: Future[T], operationName: String = "Future", timeout: FiniteDuration)(
      implicit akkaRuntime: AkkaRuntime
  ): Future[T] = {
    implicit def ec = akkaRuntime.executionContext
    val promise = Promise[T]()
    val cancellable = akkaRuntime.actorSystem.scheduler.scheduleOnce(timeout) {
      promise.tryFailure(new TimeoutException(s"$operationName timed out"))
    }
    future.onComplete { result =>
      cancellable.cancel()
      promise.tryComplete(result)
    }
    promise.future
  }

  /**
    * Runs a function `f` on a given list, each after each other. Each method receives the state returned by the function before.
    * @param in input data for the function
    * @param s0 initial state
    * @param f method consuming the current state and current element of in and returning a future to the next state.
    */
  def afterEachOtherStateful[T, S](in: Iterable[T], s0: S)(
      f: (S, T) => Future[S]
  )(implicit ec: ExecutionContext): Future[S] = {
    val result = Promise[S]()
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

  /** Runs f after each other, eventually leading to a map operation. */
  def afterEachOther[T, X](in: Iterable[T])(f: T => Future[X])(implicit ec: ExecutionContext): Future[Vector[X]] = {
    afterEachOtherStateful(in, Vector.empty[X]) { case (s, i) =>
      f(i).map { x =>
        s :+ x
      }
    }
  }

  /**
    * Try `f` multiple times within a given timeout.
    */
  def tryMultipleTimes[T](timeout: FiniteDuration, tryAgainWaitDuration: FiniteDuration)(
      f: => Future[Option[T]]
  )(implicit actorSystem: ActorSystem, ec: ExecutionContext): Future[T] = {
    val result = Promise[T]()
    val clock = Clock.systemUTC()
    val finalTimeout = clock.instant().plus(timeout.toMillis, ChronoUnit.MILLIS)
    val timeoutException = new TimeoutException(s"Timeout after ${timeout}")
    def tryAgain(): Unit = {
      f.andThen {
        case Success(None) =>
          if (clock.instant().isAfter(finalTimeout)) {
            result.tryFailure(timeoutException)
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
