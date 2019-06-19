package ai.mantik.testutils

import org.scalatest.{ Assertions, EitherValues }

/**
 * Adds helpers for unpacking Either.
 * Like [[EitherValues]] but with more debug output.
 */
trait EitherExt {
  self: Assertions =>

  class EitherExt[L, R](in: Either[L, R]) {

    def forceRight: R = {
      in match {
        case Left(value) =>
          value match {
            case t: Throwable =>
              fail(s"Got left, wanted right on try", t)
            case other =>
              fail(s"Got left, wanted right on try ${other}")
          }
        case Right(value) =>
          value
      }
    }

    def forceLeft: L = {
      in match {
        case Right(value) =>
          fail(s"Got right, wanted right on try value=${value}")
        case Left(value) =>
          value
      }
    }
  }

  import scala.language.implicitConversions
  implicit def toEitherExt[A, B](in: Either[A, B]): EitherExt[A, B] = new EitherExt[A, B](in)

}
