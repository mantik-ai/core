package ai.mantik.executor.common.test.integration

import ai.mantik.executor.Executor
import ai.mantik.testutils.TestBase

trait IntegrationBase {
  self: TestBase =>

  def withExecutor[T](f: Executor => T)

}
