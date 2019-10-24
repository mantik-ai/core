package ai.mantik.elements.errors

/** Base class for Generic Mantik Exceptions. */
abstract class MantikException(msg: String, cause: Throwable = null) extends RuntimeException(msg, cause)
