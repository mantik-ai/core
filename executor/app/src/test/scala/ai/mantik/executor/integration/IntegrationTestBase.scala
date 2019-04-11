package ai.mantik.executor.integration

import java.time.Clock

import ai.mantik.executor.{ Config, Executor }
import ai.mantik.executor.impl.{ ExecutorImpl, K8sOperations }

abstract class IntegrationTestBase extends KubernetesTestBase {

  private var _executor: ExecutorImpl = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    implicit val clock = Clock.systemUTC()
    val k8sOperations = new K8sOperations(config, _kubernetesClient)
    _executor = new ExecutorImpl(config, k8sOperations)
  }

  override protected def afterAll(): Unit = {
    _executor.shutdown()
    super.afterAll()
  }

  protected trait Env extends super.Env {
    val executor: Executor = _executor
  }
}
