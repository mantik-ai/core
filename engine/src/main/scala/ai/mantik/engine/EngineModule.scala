package ai.mantik.engine

import ai.mantik.componently.AkkaRuntime
import ai.mantik.executor.{ Executor, ExecutorModule }
import ai.mantik.executor.client.ExecutorClientProvider
import ai.mantik.planner.PlannerModule
import com.google.inject.AbstractModule

class EngineModule(isClient: Boolean = false)(implicit akkaRuntime: AkkaRuntime) extends AbstractModule {

  override def configure(): Unit = {
    install(new PlannerModule(isClient))
    if (isClient) {
      bind(classOf[Executor]).toProvider(classOf[ExecutorClientProvider])
    } else {
      install(new ExecutorModule())
    }
  }
}
