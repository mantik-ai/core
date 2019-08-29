package ai.mantik.engine

import ai.mantik.componently.AkkaRuntime
import ai.mantik.executor.{ Executor, ExecutorModule }
import ai.mantik.executor.client.ExecutorClientProvider
import ai.mantik.planner.{ ClientConfig, PlannerModule }
import com.google.inject.AbstractModule

/**
 * Configures MantikEngine Services.
 * @param clientConfig if given, configure as client.
 */
class EngineModule(clientConfig: Option[ClientConfig])(implicit akkaRuntime: AkkaRuntime) extends AbstractModule {

  override def configure(): Unit = {
    install(new PlannerModule(clientConfig))
    if (clientConfig.isDefined) {
      bind(classOf[Executor]).toProvider(classOf[ExecutorClientProvider])
    } else {
      install(new ExecutorModule())
    }
  }
}
