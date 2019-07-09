package ai.mantik.executor.integration

import java.time.Clock

import ai.mantik.executor.impl.KubernetesCleaner
import ai.mantik.executor.Config
import ai.mantik.testutils.{ AkkaSupport, TestBase }
import org.scalatest.time.{ Millis, Span }
import skuber.api.client.KubernetesClient

import scala.concurrent.duration._

abstract class KubernetesTestBase extends TestBase with AkkaSupport {

  override protected val timeout: FiniteDuration = 30.seconds

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(
    timeout = scaled(Span(30000, Millis)),
    interval = scaled(Span(500, Millis))
  )

  val config = Config().copy(
    namespacePrefix = "systemtest-",
    podTrackerId = "mantik-executor",
    podPullImageTimeout = 3.seconds,
    checkPodInterval = 1.second,
    defaultTimeout = 30.seconds,
    defaultRetryInterval = 1.second,
    interface = "localhost",
    port = 15001,
    kubernetesRetryTimes = 3
  )

  protected var _kubernetesClient: KubernetesClient = _

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
