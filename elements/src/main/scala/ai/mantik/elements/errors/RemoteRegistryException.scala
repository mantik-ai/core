package ai.mantik.elements.errors

/**
  * An Exception from the Remote Registry.
  * @param remoteCode the remote error code as in [[ai.mantik.elements.registry.api.ApiErrorResponse]].
  */
class RemoteRegistryException(
    val httpStatusCode: Int,
    val remoteCode: String,
    val message: String,
    cause: Throwable = null
) extends MantikException(ErrorCodes.RemoteRegistryFailure, message)
