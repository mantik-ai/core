package ai.mantik.componently

import com.typesafe.scalalogging.Logger

/**
 * Base trait for component like objects. This are things which need akka
 * access, have a life time.
 */
trait Component {
  implicit protected def akkaRuntime: AkkaRuntime

  /** Shut down the component. */
  def shutdown(): Unit = {}
}

/** Base class for Components. */
abstract class ComponentBase(implicit protected val akkaRuntime: AkkaRuntime) extends Component with AkkaHelper {
  /** Typesafe logger. */
  protected final val logger: Logger = Logger(getClass)
  logger.trace("Initializing...")

  override def shutdown(): Unit = {
    super.shutdown()
    logger.trace("Shutdown")
  }
}
