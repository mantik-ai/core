package io.mantik.executor.testutils

import com.typesafe.scalalogging.Logger
import org.scalatest.concurrent.Eventually
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach, FlatSpec, Matchers }

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._

/** Base class for Testcases. */
abstract class TestBase extends FlatSpec with Matchers with Eventually with BeforeAndAfterAll with BeforeAndAfterEach {
  protected val logger = Logger(getClass)

  def await[T](f: => Future[T]): T = {
    Await.result(f, 10.seconds)
  }
}
