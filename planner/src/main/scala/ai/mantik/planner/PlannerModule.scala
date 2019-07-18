package ai.mantik.planner

import ai.mantik.componently.AkkaRuntime
import ai.mantik.planner.bridge.{ BridgesProvider, Bridges }
import ai.mantik.planner.impl.{ ContextImpl, PlannerImpl }
import ai.mantik.planner.impl.exec.PlanExecutorImpl
import ai.mantik.planner.repository.{ FileRepository, Repository, RepositoryModule }
import ai.mantik.planner.repository.rpc.{ FileRepositoryClientImpl, RepositoryClientImpl }
import com.google.inject.AbstractModule

/**
 * Registers Modules inside the planner package.
 *
 * @param isClient if true, then the client variants of stateful components are initialized
 */
class PlannerModule(isClient: Boolean)(
    implicit
    akkaRuntime: AkkaRuntime
) extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[Context]).to(classOf[ContextImpl])
    bind(classOf[PlanExecutor]).to(classOf[PlanExecutorImpl])
    bind(classOf[Planner]).to(classOf[PlannerImpl])
    bind(classOf[Bridges]).toProvider(classOf[BridgesProvider])
    if (isClient) {
      bind(classOf[Repository]).to(classOf[RepositoryClientImpl])
      bind(classOf[FileRepository]).to(classOf[FileRepositoryClientImpl])
    } else {
      install(new RepositoryModule)
    }
  }
}
