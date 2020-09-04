package ai.mantik.planner

import ai.mantik.componently.AkkaRuntime
import ai.mantik.planner.impl.exec.MnpPlanExecutor
import ai.mantik.planner.impl.{ PlannerImpl, PlanningContextImpl }
import ai.mantik.planner.repository.RepositoryModule
import com.google.inject.AbstractModule

/**
 * Registers Modules inside the planner package.
 */
class PlannerModule(
    implicit
    akkaRuntime: AkkaRuntime
) extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[PlanningContext]).to(classOf[PlanningContextImpl])
    bind(classOf[PlanExecutor]).to(classOf[MnpPlanExecutor])
    bind(classOf[Planner]).to(classOf[PlannerImpl])
    install(new RepositoryModule)
  }
}
