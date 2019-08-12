package ai.mantik.planner.repository

object Errors {
  class RepositoryError(msg: String, cause: Throwable = null) extends RuntimeException(msg, cause)

  /** Some item was not found. */
  class NotFoundException(msg: String) extends RepositoryError(msg)

  /** An item may not be overwritten with the same [[ai.mantik.elements.ItemId]]. */
  class OverwriteNotAllowedException(msg: String) extends RepositoryError(msg)

  /** If some item is not the expected type. */
  class WrongTypeException(msg: String, cause: Throwable = null) extends RepositoryError(msg, cause)

  /** Something is wrong in the configuration. */
  class ConfigException(msg: String) extends RepositoryError(msg)

  /** Error talking to Registry */
  class RegistryException(msg: String, cause: Throwable = null) extends RepositoryError(msg, cause)
}
