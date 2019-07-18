package ai.mantik.engine.server

import ai.mantik.componently.{ AkkaRuntime, ComponentBase }
import ai.mantik.executor.Executor
import ai.mantik.planner.repository.{ FileRepository, Repository }
import javax.inject.{ Inject, Singleton }

/** Helper for shut down dependent services, mainly for integration tests. */
@Singleton
class ShutdownHelper @Inject() (
    fileRepository: FileRepository,
    repository: Repository,
    executor: Executor
)(implicit akkaRuntime: AkkaRuntime) extends ComponentBase {
  override def shutdown(): Unit = {
    fileRepository.shutdown()
    repository.shutdown()
    executor.shutdown()
    super.shutdown()
  }
}
