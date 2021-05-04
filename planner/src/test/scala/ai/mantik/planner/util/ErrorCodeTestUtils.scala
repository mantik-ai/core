/*
 * This file is part of the Mantik Project.
 * Copyright (c) 2020-2021 Mantik UG (Haftungsbeschränkt)
 * Authors: See AUTHORS file
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.
 *
 * Additionally, the following linking exception is granted:
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with other code, such other code is not for that reason
 * alone subject to any of the requirements of the GNU Affero GPL
 * version 3.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license.
 */
package ai.mantik.planner.util

import ai.mantik.elements.errors.{ErrorCode, MantikException}
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
