package ai.mantik.executor.kubernetes.integration

import java.time.Clock

import ai.mantik.executor.Executor
import ai.mantik.executor.kubernetes.{ KubernetesExecutor, K8sOperations }

import scala.concurrent.duration.{ FiniteDuration, _ }

abstract class IntegrationTestBase extends KubernetesTestBase {

  private var _executor: KubernetesExecutor = _

  override protected val timeout: FiniteDuration = 60.seconds

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    implicit val clock = Clock.systemUTC()
    val k8sOperations = new K8sOperations(config, _kubernetesClient)
    _executor = new KubernetesExecutor(config, k8sOperations)
  }

  override protected def afterAll(): Unit = {
    _executor.shutdown()
    super.afterAll()
  }

  protected trait Env extends super.Env {
    val executor: Executor = _executor
  }
}
