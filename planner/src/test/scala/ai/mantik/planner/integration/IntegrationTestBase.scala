package ai.mantik.planner.integration

import ai.mantik.executor.kubernetes.{ ExecutorForIntegrationTests, KubernetesCleaner }
import ai.mantik.planner.Context
import ai.mantik.planner.impl.ContextImpl
import ai.mantik.planner.util.TestBaseWithAkkaRuntime
import com.typesafe.config.{ Config, ConfigFactory }
import org.scalatest.time.{ Millis, Span }

import scala.concurrent.duration._

/** Base class for integration tests having a full running executor instance. */
abstract class IntegrationTestBase extends TestBaseWithAkkaRuntime {

  protected var embeddedExecutor: ExecutorForIntegrationTests = _
  override protected lazy val typesafeConfig: Config = ConfigFactory.load("systemtest.conf")
  private var _context: Context = _

  implicit def context: Context = _context

  override protected val timeout: FiniteDuration = 30.seconds

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(
    timeout = scaled(Span(30000, Millis)),
    interval = scaled(Span(500, Millis))
  )

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    embeddedExecutor = new ExecutorForIntegrationTests(typesafeConfig)
    scrapKubernetes()
    _context = ContextImpl.constructTempClient()
  }

  private def scrapKubernetes(): Unit = {
    val cleaner = new KubernetesCleaner(embeddedExecutor.kubernetesClient, embeddedExecutor.executorConfig)
    cleaner.deleteKubernetesContent()
  }

  override protected def afterAll(): Unit = {
    embeddedExecutor.stop()
    super.afterAll()
  }
}
