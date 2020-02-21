package ai.mantik.executor.kubernetes.integration

import java.time.Clock

import ai.mantik.executor.Executor
import ai.mantik.executor.common.test.integration.IntegrationBase
import ai.mantik.executor.kubernetes.{K8sOperations, KubernetesExecutor}

import scala.concurrent.duration.{FiniteDuration, _}

abstract class IntegrationTestBase extends KubernetesTestBase with IntegrationBase {

  private var _executor: KubernetesExecutor = _

  override protected val timeout: FiniteDuration = 60.seconds

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    implicit val clock = Clock.systemUTC()
    val k8sOperations = new K8sOperations(config, _kubernetesClient)
    _executor = new KubernetesExecutor(config, k8sOperations)
  }

  protected trait Env extends super.Env {
    val executor: Executor = _executor
  }

  override def withExecutor[T](f: Executor => T): Unit = {
    val env = new Env {}
    f(env.executor)
  }
}
