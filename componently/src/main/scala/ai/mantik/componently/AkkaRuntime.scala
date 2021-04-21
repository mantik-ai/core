package ai.mantik.componently

import java.time.Clock

import ai.mantik
import ai.mantik.componently
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

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
  def lifecycle: Lifecycle

  /**
    * Shutdown whats belonging to this runtime.
    * If it manges Akka by itself, it will be shut down.
    */
  def shutdownAsync(): Future[_]

  /**
    * Returns a new AkkaRuntime with it's own lifecycle.
    * Useful for testing components.
    */
  def withExtraLifecycle(): AkkaRuntime

  final def shutdown(): Unit = Await.ready(shutdownAsync(), AkkaRuntime.ShutdownTimeout)
}

object AkkaRuntime {

  val ShutdownTimeout: FiniteDuration = 1.minutes

  /** Create an AkkaRuntime instance from running Akka components. */
  def fromRunning(
      config: Config = ConfigFactory.load(),
      clock: Clock = Clock.systemUTC()
  )(implicit actorSystem: ActorSystem, ec: ExecutionContext, m: Materializer): AkkaRuntime = {
    val lifecycle = new Lifecycle.SimpleLifecycle()
    actorSystem.registerOnTermination {
      Await.result(lifecycle.shutdown(), ShutdownTimeout)
    }
    AkkaRuntimeImpl(config, m, ec, actorSystem, clock, lifecycle, ownAkka = false)
  }

  /** Create an AkkaRuntime instance from initializing new akka. */
  def createNew(
      config: Config = ConfigFactory.load(),
      clock: Clock = Clock.systemUTC()
  ): AkkaRuntime = {
    implicit val actorSystem: ActorSystem = ActorSystem("default", config)
    implicit val materializer: Materializer = ActorMaterializer.create(actorSystem)
    implicit val ec = actorSystem.dispatcher
    val lifecycle = new Lifecycle.SimpleLifecycle()
    AkkaRuntimeImpl(config, materializer, ec, actorSystem, clock, lifecycle, ownAkka = true)
  }
}

private[componently] case class AkkaRuntimeImpl(
    config: Config,
    materializer: Materializer,
    executionContext: ExecutionContext,
    actorSystem: ActorSystem,
    clock: Clock,
    lifecycle: Lifecycle,
    ownAkka: Boolean
) extends AkkaRuntime {

  override def withExtraLifecycle(): AkkaRuntime = {
    import actorSystem.dispatcher
    copy(
      ownAkka = false,
      lifecycle = new mantik.componently.Lifecycle.SimpleLifecycle()
    )
  }

  override def shutdownAsync(): Future[_] = {
    import actorSystem.dispatcher
    lifecycle.shutdown().flatMap { _ =>
      if (ownAkka) {
        actorSystem.terminate()
      } else {
        Future.successful(())
      }
    }
  }
}
