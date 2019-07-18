package ai.mantik.componently

import java.time.Clock

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.typesafe.config.Config

import scala.concurrent.ExecutionContext

/**
 * Helper for implementing [[Component]], gives implicit access to Akka Components
 */
trait AkkaHelper {
  self: Component =>

  protected def config: Config = akkaRuntime.config

  protected def clock: Clock = akkaRuntime.clock

  // Gives implicit access to various core Akka Services.
  protected implicit def executionContext: ExecutionContext = akkaRuntime.executionContext
  protected implicit def materializer: Materializer = akkaRuntime.materializer
  protected implicit def actorSystem: ActorSystem = akkaRuntime.actorSystem
}

/**
 * Similar to AkkaHelper trait, but can be imported where you do not want to change trait hierarchy.
 */
object AkkaHelper {
  def config(implicit akkaRuntime: AkkaRuntime): Config = akkaRuntime.config

  implicit def executionContext(implicit akkaRuntime: AkkaRuntime): ExecutionContext = akkaRuntime.executionContext
  implicit def materializer(implicit akkaRuntime: AkkaRuntime): Materializer = akkaRuntime.materializer
  implicit def actorSystem(implicit akkaRuntime: AkkaRuntime): ActorSystem = akkaRuntime.actorSystem
}
