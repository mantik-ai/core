package ai.mantik.ds

object Errors {
  /** A type with given name was not found. */
  class TypeNotFoundException(msg: String) extends RuntimeException(msg)

  /** A format like this is not supported. */
  class FormatNotSupportedException(msg: String) extends RuntimeException(msg)

  /** A Format definition is in a way not logical. */
  class FormatDefinitionException(msg: String) extends RuntimeException(msg)

  /** Something is not encoded in the way we expect it. */
  class EncodingException(msg: String) extends RuntimeException(msg)
}
