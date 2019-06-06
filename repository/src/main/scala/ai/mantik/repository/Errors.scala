package ai.mantik.repository

object Errors {
  class RepositoryError(msg: String, cause: Throwable = null) extends RuntimeException(msg, cause)

  /** Some item was not found. */
  class NotFoundException(msg: String) extends RepositoryError(msg)

  /** If some item is not the expected type. */
  class WrongTypeException(msg: String) extends RepositoryError(msg)
}