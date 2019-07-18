package ai.mantik.testutils

import com.typesafe.config.{ Config, ConfigFactory }
import com.typesafe.scalalogging.Logger
import org.scalatest.concurrent.Eventually
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach, FlatSpec, Matchers }

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

abstract class TestBase extends FlatSpec with Matchers with BeforeAndAfterEach with BeforeAndAfterAll with Eventually with EitherExt {

  protected val timeout: FiniteDuration = 10.seconds
  protected final val logger = Logger(getClass)

  protected lazy val typesafeConfig: Config = ConfigFactory.load()

  def await[T](f: => Future[T]): T = {
    Await.result(f, timeout)
  }
}
