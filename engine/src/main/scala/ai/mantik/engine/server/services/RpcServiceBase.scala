package ai.mantik.engine.server.services

import ai.mantik.componently.ComponentBase
import com.typesafe.scalalogging.Logger

import scala.concurrent.Future
import scala.util.{ Failure, Success }
import scala.util.control.NonFatal

/** Common base trait for gRpc service implementations talking to Mantik Services. */
private[services] trait RpcServiceBase {
  self: ComponentBase =>

  /**
   * Handle errors. gRpc likes to just forwards them without any backtrace.
   * However we want them to be logged and perhaps translated.
   */
  protected def handleErrors[T](f: => Future[T]): Future[T] = {
    val result = try {
      f
    } catch {
      case NonFatal(e) =>
        // this is bad, the error happened while creating the future
        logger.error("Unhandled error, not in future", e)
        throw e
    }
    result.transform {
      case Success(value) => Success(value)
      case Failure(NonFatal(e)) if translateError.isDefinedAt(e) =>
        logger.debug("Something failed", e)
        Failure(translateError(e))
      case Failure(NonFatal(e)) =>
        logger.warn("Something failed", e)
        Failure(e)
    }
  }

  /** The place to add more error handlers. */
  protected val translateError: PartialFunction[Throwable, Throwable] = PartialFunction.empty
}
