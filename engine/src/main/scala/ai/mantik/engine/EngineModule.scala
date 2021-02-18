package ai.mantik.engine

import ai.mantik.componently.AkkaRuntime
import ai.mantik.executor.{ ExecutorFileStorageModule, ExecutorModule }
import ai.mantik.planner.PlannerModule
import com.google.inject.AbstractModule

/**
 * Configures MantikEngine Services.
 */
class EngineModule()(implicit akkaRuntime: AkkaRuntime) extends AbstractModule {

  override def configure(): Unit = {
    install(new PlannerModule())
    install(new ExecutorModule())
    install(new ExecutorFileStorageModule())
  }
}
