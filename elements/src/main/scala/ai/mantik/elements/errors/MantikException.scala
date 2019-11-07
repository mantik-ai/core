package ai.mantik.elements.errors

import io.grpc.{ Metadata, StatusRuntimeException }

/** Base class for Mantik Exceptions. */
class MantikException(val code: ErrorCode, msg: String, cause: Throwable = null) extends RuntimeException(msg, cause) {
  /** Serialize the exception into it's code and message. */
  def serialize(): (String, Option[String]) = code.code -> Option(getMessage).filter(_.nonEmpty)

  /** Convert the exception into it's gRpc representation. */
  def toGrpc: StatusRuntimeException = {
    val status = code.grpcCode.toStatus
      .withDescription(msg)
      .withCause(cause)

    val metaData = new Metadata()
    metaData.put(MantikException.ErrorCodeMetaDataKey, code.code)

    new StatusRuntimeException(status, metaData)
  }
}

/** A Mantik Exception which happened remote (gRpc), does not have a stack trace. */
class MantikRemoteException(code: ErrorCode, msg: String) extends MantikException(code, msg) {

  override def fillInStackTrace(): Throwable = {
    // does nothing
    this
  }

  private[errors] def addBacktrace(): Unit = {
    super.fillInStackTrace()
  }

}

object MantikRemoteException {

  /** Convert the Exception from it's gRpc Representation. */
  def fromGrpc(statusRuntimeException: StatusRuntimeException): MantikRemoteException = {
    Option(statusRuntimeException.getTrailers.get(MantikException.ErrorCodeMetaDataKey)) match {
      case None =>
        val result = new MantikRemoteException(ErrorCodes.InternalError, s"Undecodable error, ${statusRuntimeException.getMessage}")
        // interesting where the error codes from
        result.addBacktrace()
        result
      case Some(code) =>
        val decoded = new ErrorCode(code, statusRuntimeException.getStatus.getCode)
        val result = new MantikRemoteException(decoded, statusRuntimeException.getMessage)
        result
    }
  }
}

object MantikException {

  /** Metadata key for the error code. */
  val ErrorCodeMetaDataKey = Metadata.Key.of("errorcode", Metadata.ASCII_STRING_MARSHALLER)
}