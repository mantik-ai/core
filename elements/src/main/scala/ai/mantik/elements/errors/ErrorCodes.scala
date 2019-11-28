package ai.mantik.elements.errors

import io.grpc.Status.Code

/**
 * Main Error codes used for [[MantikException]].
 * They provide enough information for serializing/deserializing common error cases.
 */
object ErrorCodes {
  private val defaultCodeBuilder = Seq.newBuilder[ErrorCode]

  private def add(errorCode: ErrorCode): ErrorCode = {
    defaultCodeBuilder += errorCode
    errorCode
  }

  private def addCode(code: String, grpcCode: Code): ErrorCode = {
    val c = RootCode.derive(code, Some(grpcCode))
    defaultCodeBuilder += c
    c
  }

  val RootCode = new ErrorCode("", Code.UNKNOWN)

  val MantikItem = addCode("MantikItem", Code.INVALID_ARGUMENT)

  val MantikItemNotFound = add(MantikItem.derive("NotFound", Some(Code.NOT_FOUND)))

  val MantikItemPayloadNotFound = add(MantikItem.derive("PayloadNotFound", Some(Code.NOT_FOUND)))

  val MantikItemWrongType = add(MantikItem.derive("WrongType", Some(Code.INVALID_ARGUMENT)))

  val MantikItemConflict = add(MantikItem.derive("Conflict", Some(Code.FAILED_PRECONDITION)))

  val MantikItemInvalidBridge = add(MantikItem.derive("InvalidBridge", Some(Code.FAILED_PRECONDITION)))

  val InvalidMantikfile = addCode("InvalidMantikfile", Code.INVALID_ARGUMENT)

  val InvalidMantikId = addCode("InvalidMantikId", Code.INVALID_ARGUMENT)

  val Configuration = addCode("Configuration", Code.INTERNAL)

  val LocalRegistry = addCode("LocalRegistry", Code.INTERNAL)

  val RemoteRegistryFailure = addCode("RemoteRegistry", Code.UNKNOWN)

  val RemoteRegistryCouldNotGetToken = add(RemoteRegistryFailure.derive("CouldNotGetToken"))

  val InternalError = addCode("Internal", Code.INTERNAL)

  /** Return a list of default error codes. */
  lazy val defaultErrorCodes: Seq[ErrorCode] = defaultCodeBuilder.result()
}
