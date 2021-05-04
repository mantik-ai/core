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
package ai.mantik.testutils

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.Logger
import org.scalactic.source
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FlatSpec, Matchers}

import scala.concurrent.{Await, ExecutionException, Future}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.util.control.NonFatal

abstract class TestBase
    extends FlatSpec
    with Matchers
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with Eventually
    with EitherExt {

  protected val timeout: FiniteDuration = 10.seconds
  protected final val logger = Logger(getClass)

  protected lazy val typesafeConfig: Config = ConfigFactory.load()

  /**
    * Wait for a result and returns the future.
    * Note: if you are expecting a failure, use [[awaitException()]]
    */
  @throws[ExecutionException]("If the future returns an error (to have a stack trace)")
  def await[T](f: => Future[T]): T = {
    try {
      Await.result(f, timeout)
    } catch {
      case NonFatal(e) =>
        throw new ExecutionException(s"Asynchronous operation failed", e)
    }
  }

  /** Await an exception (doesn't log if the exception occurs) */
  def awaitException[T <: Throwable](f: => Future[_])(implicit classTag: ClassTag[T], pos: source.Position): T = {
    intercept[T] {
      Await.result(f, timeout)
    }
  }

  protected def ensureSameElements[T](left: Seq[T], right: Seq[T]): Unit = {
    val missingRight = left.diff(right)
    val missingLeft = right.diff(left)
    if (missingLeft.nonEmpty || missingRight.nonEmpty) {
      fail(s"""
              |Two sets do not contain the same elements.
              |
              |** Missing Left **:
              |${missingLeft.mkString("\n")}
              |
              |** Missing Right **:
              |${missingRight.mkString("\n")}
              |""".stripMargin)
    }
  }
}
