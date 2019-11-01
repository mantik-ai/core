package ai.mantik.elements.errors

/** An exception when talking to the Mantik local / remote Registry.  */
class RegistryException(msg: String, cause: Throwable = null) extends MantikException(msg, cause)

/** There was an invalid login when talking to the Registry. */
class InvalidLoginException(msg: String, cause: Throwable = null) extends RegistryException(msg, cause)