package ai.mantik.planner.integration

import ai.mantik.componently.AkkaRuntime
import ai.mantik.executor.kubernetes.{ ExecutorForIntegrationTests, KubernetesCleaner }
import ai.mantik.testutils.{ AkkaSupport, TestBase }
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
  protected var context: Context = _

  override protected val timeout: FiniteDuration = 30.seconds

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(
    timeout = scaled(Span(30000, Millis)),
    interval = scaled(Span(500, Millis))
  )

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    embeddedExecutor = new ExecutorForIntegrationTests(typesafeConfig)
    context = ContextImpl.constructForLocalTestingWithAkka()
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
