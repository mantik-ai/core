package io.mantik.executor.integration

import java.time.Clock

import io.mantik.executor.{ Config, Executor }
import io.mantik.executor.impl.ExecutorImpl

abstract class IntegrationTestBase extends KubernetesTestBase {

  private var _executor: ExecutorImpl = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    implicit val clock = Clock.systemUTC()
    _executor = new ExecutorImpl(config, _kubernetesClient)
  }

  override protected def afterAll(): Unit = {
    _executor.shutdown()
    super.afterAll()
  }

  protected trait Env extends super.Env {
    val executor: Executor = _executor
  }
}
