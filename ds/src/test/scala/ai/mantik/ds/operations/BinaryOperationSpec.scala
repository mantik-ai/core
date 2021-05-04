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
package ai.mantik.ds.operations

import ai.mantik.testutils.TestBase
import io.circe.Json
import io.circe.syntax._

class BinaryOperationSpec extends TestBase {

  it should "translate well to JSON" in {
    (BinaryOperation.Mul: BinaryOperation).asJson shouldBe Json.fromString("mul")
    Json.fromString("mul").as[BinaryOperation] shouldBe Right(BinaryOperation.Mul)
    for {
      x <- Seq
        .apply[BinaryOperation](BinaryOperation.Add, BinaryOperation.Sub, BinaryOperation.Mul, BinaryOperation.Div)
    } {
      x.asJson.as[BinaryOperation] shouldBe Right(x)
    }
  }
}
