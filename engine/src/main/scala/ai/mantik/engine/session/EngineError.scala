package ai.mantik.engine.session

import ai.mantik.elements.errors.ErrorCode
import io.grpc.Status.Code

object EngineErrors {
  /** There was an engine specific error. */
  val EngineError = new ErrorCode("Engine")

  /** The session was not found. */
  val SessionNotFound = EngineError.derive("SessionNotFound", grpcCode = Some(Code.NOT_FOUND))

  /** The item you wanted to combine has not the reqired type. */
  val ItemUnexpectedType = EngineError.derive("ItemUnexpectedType", grpcCode = Some(Code.INVALID_ARGUMENT))

  /** The item was not found in the session. */
  val ItemNotFoundInSession = EngineError.derive("ItemNotFoundInSession", grpcCode = Some(Code.NOT_FOUND))
}
