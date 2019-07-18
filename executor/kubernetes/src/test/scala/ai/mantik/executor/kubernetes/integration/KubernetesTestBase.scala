package ai.mantik.executor.kubernetes.integration

import java.time.Clock

import ai.mantik.componently.AkkaRuntime
import ai.mantik.executor.kubernetes.{ Config, KubernetesCleaner }
import ai.mantik.testutils.{ AkkaSupport, TestBase }
import com.typesafe.config.ConfigFactory
import org.scalatest.time.{ Millis, Span }
import skuber.api.client.KubernetesClient

import scala.concurrent.duration._

abstract class KubernetesTestBase extends TestBase with AkkaSupport {

  override protected val timeout: FiniteDuration = 30.seconds

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(
    timeout = scaled(Span(30000, Millis)),
    interval = scaled(Span(500, Millis))
  )

  override lazy val typesafeConfig = ConfigFactory.load("systemtest.conf")
  val config = Config.fromTypesafeConfig(typesafeConfig)

  protected var _kubernetesClient: KubernetesClient = _

  protected implicit lazy val akkaRuntime = AkkaRuntime.fromRunning(typesafeConfig)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    _kubernetesClient = skuber.k8sInit(actorSystem, materializer)
    implicit val clock = Clock.systemUTC()
    val cleaner = new KubernetesCleaner(_kubernetesClient, config)
    cleaner.deleteKubernetesContent()
  }

  override protected def afterAll(): Unit = {
    _kubernetesClient.close
    super.afterAll()
  }

  protected trait Env {
    val kubernetesClient = _kubernetesClient
  }
}
