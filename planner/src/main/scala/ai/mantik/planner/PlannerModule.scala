package ai.mantik.planner

import ai.mantik.componently.AkkaRuntime
import ai.mantik.planner.impl.{ ContextImpl, PlannerImpl }
import ai.mantik.planner.impl.exec.{ MnpPlanExecutor, PlanExecutorImpl }
import ai.mantik.planner.repository.{ FileRepository, FileRepositoryServer, Repository, RepositoryModule }
import ai.mantik.planner.repository.rpc.{ FileRepositoryClientImpl, RepositoryClientImpl }
import com.google.inject.AbstractModule

/**
 * Registers Modules inside the planner package.
 *
 * @param isClient if given, then configure as client.
 */
class PlannerModule(isClient: Option[ClientConfig])(
    implicit
    akkaRuntime: AkkaRuntime
) extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[Context]).to(classOf[ContextImpl])
    bind(classOf[PlanExecutor]).to(classOf[MnpPlanExecutor])
    bind(classOf[Planner]).to(classOf[PlannerImpl])
    isClient match {
      case Some(clientConfig) =>
        bind(classOf[ClientConfig]).toInstance(clientConfig)
        bind(classOf[Repository]).to(classOf[RepositoryClientImpl])
        bind(classOf[FileRepository]).to(classOf[FileRepositoryClientImpl])
      case None =>
        install(new RepositoryModule)
        bind(classOf[ClientConfig]).toProvider(classOf[ClientConfigProvider])
    }
  }
}
