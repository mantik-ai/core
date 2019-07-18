package ai.mantik.componently.di

import ai.mantik.componently.AkkaRuntime
import com.google.inject.AbstractModule
import com.typesafe.config.Config

/** A module which registers the Akka Runtime for DI. */
class AkkaModule(implicit akkaRuntime: AkkaRuntime) extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[AkkaRuntime]).toInstance(akkaRuntime)
    bind(classOf[Config]).toInstance(akkaRuntime.config)
  }
}
