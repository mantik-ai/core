package ai.mantik.ds.testutil

import org.scalatest.concurrent.Eventually
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach, FlatSpec, Matchers }
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext, Future }

abstract class TestBase extends FlatSpec with Matchers with BeforeAndAfterAll with BeforeAndAfterEach with Eventually {

  implicit def ec: ExecutionContext = ExecutionContext.global

  protected val logger = LoggerFactory.getLogger(getClass)

  def await[T](f: => Future[T]): T = {
    Await.result(f, 10.seconds)
  }
}
