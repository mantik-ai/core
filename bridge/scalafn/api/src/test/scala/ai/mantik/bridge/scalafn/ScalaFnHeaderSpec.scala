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
package ai.mantik.bridge.scalafn

import ai.mantik.ds.{ FundamentalType, TabularData }
import ai.mantik.elements.CombinerDefinition
import ai.mantik.testutils.TestBase
import io.circe.Json
import io.circe.syntax._

class ScalaFnHeaderSpec extends TestBase {
  it should "serialize well" in {
    val header = ScalaFnHeader(
      combiner = CombinerDefinition(
        bridge = "myBridge",
        input = Vector(
          TabularData("x" -> FundamentalType.Int32)
        ),
        output = Vector(
          TabularData("y" -> FundamentalType.Int32)
        )
      ),
      fnType = ScalaFnType.RowMapperType
    )
    val json = header.asJson
    json.asObject.get.toMap.get("fnType") shouldBe Some(Json.fromString("rowMapper"))
    json.as[ScalaFnHeader] shouldBe Right(header)
  }
}
