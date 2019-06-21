package ai.mantik.planner.utils

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.typesafe.config.Config

import scala.concurrent.ExecutionContext

/**
 * Base trait for component like objects. This are things which need akka
 * access, have a life time.
 */
trait Component {
  protected def akkaRuntime: AkkaRuntime
  // Gives access to various core Akka Services.
  protected implicit def executionContext: ExecutionContext = akkaRuntime.executionContext
  protected implicit def materializer: Materializer = akkaRuntime.materializer
  protected implicit def actorSystem: ActorSystem = akkaRuntime.actorSystem

  def config: Config = akkaRuntime.config

  /** Shut down the component. */
  def shutdown(): Unit = {}
}

/** Base class for Components. */
abstract class ComponentBase(implicit protected val akkaRuntime: AkkaRuntime) extends Component {

}
