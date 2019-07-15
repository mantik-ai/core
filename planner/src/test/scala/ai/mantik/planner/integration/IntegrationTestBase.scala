package ai.mantik.planner.integration

import ai.mantik.executor.impl.KubernetesCleaner
import ai.mantik.executor.{ Config, ExecutorForIntegrationTests }
import ai.mantik.testutils.{ AkkaSupport, TestBase }
import ai.mantik.planner.Context
import ai.mantik.planner.utils.AkkaRuntime
import com.typesafe.config.ConfigFactory
import org.scalatest.time.{ Millis, Span }

import scala.concurrent.duration._

/** Base class for integration tests having a full running executor instance. */
abstract class IntegrationTestBase extends TestBase with AkkaSupport {

  protected var embeddedExecutor: ExecutorForIntegrationTests = _
  protected val config = ConfigFactory.load()
  protected var context: Context = _

  override protected val timeout: FiniteDuration = 30.seconds

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(
    timeout = scaled(Span(30000, Millis)),
    interval = scaled(Span(500, Millis))
  )

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    embeddedExecutor = new ExecutorForIntegrationTests()
    implicit val runtime = AkkaRuntime.fromRunning(config)
    context = Context.localWithAkka()
    scrapKubernetes()
  }

  private def scrapKubernetes(): Unit = {
    val cleaner = new KubernetesCleaner(embeddedExecutor.kubernetesClient, embeddedExecutor.executorConfig)
    cleaner.deleteKubernetesContent()
  }

  override protected def afterAll(): Unit = {
    embeddedExecutor.shutdown()
    context.shutdown()
    super.afterAll()
  }
}
