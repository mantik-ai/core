package ai.mantik.elements.errors

import io.grpc.Status.Code

/**
 * A (hierarchical) error code.
 *
 * In contrast to exceptions they can be transferred via gRpc and HTTP.
 *
 * @param code error code. Sub codes are handled by adding slashes.
 */
class ErrorCode(
    val code: String,
    val grpcCode: Code = Code.UNKNOWN
) {

  /** The code cut as path. */
  lazy val codePath = code.split('/').toIndexedSeq.filter(_.nonEmpty)

  override def equals(obj: Any): Boolean = {
    obj match {
      case e: ErrorCode if code == e.code => true
      case _                              => false
    }
  }

  /** Throw this error code as exception. */
  def throwIt(msg: String, cause: Throwable = null): Nothing = {
    throw toException(msg, cause)
  }

  /** Convert this error code into a exception. */
  def toException(msg: String, cause: Throwable = null): MantikException = {
    new MantikException(this, code + ": " + msg, cause)
  }

  override def hashCode(): Int = {
    code.hashCode
  }

  def isA(errorCode: ErrorCode): Boolean = {
    codePath.startsWith(errorCode.codePath)
  }

  /** Derive a new sub code taking over existing fields. */
  def derive(
    subCode: String,
    grpcCode: Option[Code] = None
  ): ErrorCode = {
    val fullCode = if (code == "") {
      subCode
    } else {
      code + "/" + subCode
    }
    new ErrorCode(
      fullCode,
      grpcCode.getOrElse(this.grpcCode)
    )
  }
}
