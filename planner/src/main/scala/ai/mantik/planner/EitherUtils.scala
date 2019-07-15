package ai.mantik.planner
import scala.language.implicitConversions

/** Extend Either. */
object EitherUtils {

  /** Extensions for Either where the left value is something throwable. */
  class ThrowableEitherExt[L <: Throwable, R](in: Either[L, R]) {
    def force: R = {
      in match {
        case Left(value)  => throw value
        case Right(value) => value
      }
    }
  }

  /** Extension for Either where the left value is an error string. */
  class StringErrorEither[R](in: Either[String, R]) {
    def force: R = {
      in match {
        case Left(error)  => throw new NoSuchElementException(s"Got left with error message ${error}")
        case Right(value) => value
      }
    }
  }

  implicit def toThrowableEither[L <: Throwable, R](in: Either[L, R]): ThrowableEitherExt[L, R] = new ThrowableEitherExt[L, R](in)

  implicit def toStringErrorEither[R](in: Either[String, R]): StringErrorEither[R] = new StringErrorEither[R](in)
}
