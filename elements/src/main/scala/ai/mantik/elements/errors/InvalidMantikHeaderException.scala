package ai.mantik.elements.errors

/** Invalid MantikHeader. */
class InvalidMantikHeaderException(msg: String, cause: Throwable = null)
    extends MantikException(ErrorCodes.InvalidMantikHeader, msg, cause)

object InvalidMantikHeaderException {

  /** Wrap an exception into an InvalidMantikHeaderException. */
  def wrap(e: Throwable): InvalidMantikHeaderException = {
    e match {
      case a: InvalidMantikHeaderException => a
      case other                           => new InvalidMantikHeaderException(Option(e.getMessage).getOrElse(other.getClass.getSimpleName), e)
    }
  }
}
