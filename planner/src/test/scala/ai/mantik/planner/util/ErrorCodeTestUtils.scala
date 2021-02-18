package ai.mantik.planner.util

import ai.mantik.elements.errors.{ ErrorCode, MantikException }
import ai.mantik.testutils.TestBase

import scala.concurrent.Future

trait ErrorCodeTestUtils {
  self: TestBase =>
  def interceptErrorCode(code: ErrorCode)(f: => Unit): MantikException = {
    val e = intercept[MantikException] {
      f
    }
    withClue(s"Expected error code ${code} must match ${e.code}") {
      e.code.isA(code) shouldBe true
    }
    e
  }

  def awaitErrorCode(code: ErrorCode)(f: => Future[_]): MantikException = {
    val e = awaitException[MantikException] {
      f
    }
    withClue(s"Expected error code ${code} must match ${e.code}") {
      e.code.isA(code) shouldBe true
    }
    e
  }
}
