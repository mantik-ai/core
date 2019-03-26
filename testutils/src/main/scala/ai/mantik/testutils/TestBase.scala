package ai.mantik.testutils

import org.scalatest.concurrent.Eventually
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach, FlatSpec, Matchers }
import org.slf4j.LoggerFactory

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

abstract class TestBase extends FlatSpec with Matchers with BeforeAndAfterEach with BeforeAndAfterAll with Eventually {

  protected val timeout: FiniteDuration = 10.seconds
  protected val logger = LoggerFactory.getLogger(getClass)

  def await[T](f: => Future[T]): T = {
    Await.result(f, timeout)
  }
}
