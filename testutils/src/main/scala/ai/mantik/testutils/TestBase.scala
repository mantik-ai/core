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
