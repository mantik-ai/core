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
package ai.mantik.ds.sql.run

import ai.mantik.ds.element.ValueEncoder
import ai.mantik.ds.testutil.TestBase

class ProgramRunnerSpec extends TestBase {

  it should "work for a simple program" in {
    val program = Program(
      args = 2,
      stackInitDepth = 1,
      retStackDepth = 1,
      ops = Vector(
        OpCode.Get(1),
        OpCode.Get(0)
      )
    )
    val runner = new ProgramRunner(program)
    runner.run(IndexedSeq(ValueEncoder(1), ValueEncoder(2))) shouldBe IndexedSeq(ValueEncoder(2), ValueEncoder(1))
  }
}
