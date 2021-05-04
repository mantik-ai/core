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

import ai.mantik.ds.FundamentalType
import ai.mantik.ds.element.Bundle
import ai.mantik.ds.operations.BinaryOperation
import ai.mantik.ds.testutil.TestBase
import io.circe.syntax._

class ProgramSpec extends TestBase {

  "fromOps" should "work in empty case" in {
    Program.fromOps(Vector.empty) shouldBe Program(
      0,
      0,
      0,
      Vector.empty
    )
  }

  it should "work in average case" in {
    val ops = Vector(
      OpCode.Constant(Bundle.fundamental(1)),
      OpCode.Get(0),
      OpCode.Equals(FundamentalType.Int32),
      OpCode.Neg,
      OpCode.Get(1),
      OpCode.Equals(FundamentalType.Int32),
      OpCode.ReturnOnFalse,
      OpCode.Pop,
      OpCode.Constant(Bundle.fundamental(1))
    )
    Program.fromOps(
      ops
    ) shouldBe Program(
      args = 2,
      retStackDepth = 1,
      stackInitDepth = 2,
      ops = ops
    )
  }

  "json" should "work for empty case" in {
    Program().asJson.as[Program] shouldBe Right(Program())
  }

  it should "work for all op codes" in {
    val program = Program(
      100,
      200,
      300,
      Vector(
        OpCode.Get(1),
        OpCode.Pop,
        OpCode.Constant(Bundle.fundamental("Hello World")),
        OpCode.Cast(FundamentalType.Int32, FundamentalType.Int64),
        OpCode.Neg,
        OpCode.And,
        OpCode.Or,
        OpCode.Equals(FundamentalType.StringType),
        OpCode.ReturnOnFalse,
        OpCode.BinaryOp(FundamentalType.Int32, BinaryOperation.Add),
        OpCode.IsNull,
        OpCode.UnpackNullableJump(1, 2),
        OpCode.PackNullable,
        OpCode.ArraySize,
        OpCode.ArrayGet,
        OpCode.StructGet(1)
      )
    )
    program.asJson.as[Program] shouldBe Right(program)
  }
}
