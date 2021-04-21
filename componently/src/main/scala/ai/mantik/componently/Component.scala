package ai.mantik.componently

import com.typesafe.scalalogging.Logger

import scala.concurrent.Future

/**
  * Base trait for component like objects. This are things which need akka
  * access, have a life time.
  */
trait Component {
  implicit protected def akkaRuntime: AkkaRuntime
}

/** Base class for Components. */
abstract class ComponentBase(implicit protected val akkaRuntime: AkkaRuntime) extends Component with AkkaHelper {

  /** Typesafe logger. */
  protected final val logger: Logger = Logger(getClass)
  logger.trace("Initializing...")

  protected def addShutdownHook(f: => Future[_]): Unit = {
    akkaRuntime.lifecycle.addShutdownHook {
      logger.trace("Shutdown")
      f
    }
  }
}
