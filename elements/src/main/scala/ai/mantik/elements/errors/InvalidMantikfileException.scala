package ai.mantik.elements.errors

/** Invalid Mantikfile. */
class InvalidMantikfileException(msg: String, cause: Throwable = null) extends MantikException(ErrorCodes.InvalidMantikfile, msg, cause)

object InvalidMantikfileException {
  /** Wrap an exception into an InvalidMantikfileException. */
  def wrap(e: Throwable): InvalidMantikfileException = {
    e match {
      case a: InvalidMantikfileException => a
      case other                         => new InvalidMantikfileException(Option(e.getMessage).getOrElse(other.getClass.getSimpleName), e)
    }
  }
}
