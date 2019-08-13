package ai.mantik.componently

import java.time.Clock

import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, Materializer }
import com.typesafe.config.{ Config, ConfigFactory, ConfigValueFactory }

import scala.concurrent.ExecutionContext

/**
 * Encapsulates access to various used Akka Components.
 * and underlying stuff.
 */
trait AkkaRuntime {
  def config: Config
  def clock: Clock
  def materializer: Materializer
  def executionContext: ExecutionContext
  def actorSystem: ActorSystem

  /** Override config values. */
  def withConfigOverrides(
    keyValues: (String, AnyRef)*
  ): AkkaRuntime = {
    if (keyValues.isEmpty) {
      this
    } else {
      val newConfig = keyValues.foldLeft(config) {
        case (c, (key, value)) =>
          c.withValue(key, ConfigValueFactory.fromAnyRef(value))
      }
      AkkaRuntimeImpl(newConfig, materializer, executionContext, actorSystem, clock)
    }
  }

  /** Shutdown Akka. */
  def shutdown(): Unit
}

object AkkaRuntime {

  /** Create an AkkaRuntime instance from running Akka components. */
  def fromRunning(
    config: Config = ConfigFactory.load(),
    clock: Clock = Clock.systemUTC()
  )(implicit actorSystem: ActorSystem, ec: ExecutionContext, m: Materializer): AkkaRuntime = {
    AkkaRuntimeImpl(config, m, ec, actorSystem, clock)
  }

  /** Create an AkkaRuntime instance from initializing new akka. */
  def createNew(
    config: Config = ConfigFactory.load(),
    clock: Clock = Clock.systemUTC()
  ): AkkaRuntime = {
    implicit val actorSystem: ActorSystem = ActorSystem("default", config)
    implicit val materializer: Materializer = ActorMaterializer.create(actorSystem)
    implicit val ec = actorSystem.dispatcher
    fromRunning(config, clock)
  }
}

private[componently] case class AkkaRuntimeImpl(
    config: Config,
    materializer: Materializer,
    executionContext: ExecutionContext,
    actorSystem: ActorSystem,
    clock: Clock
) extends AkkaRuntime {
  override def shutdown(): Unit = actorSystem.terminate()
}
