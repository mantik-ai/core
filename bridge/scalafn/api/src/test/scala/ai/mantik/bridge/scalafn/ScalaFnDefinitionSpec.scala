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

import ai.mantik.ds.FundamentalType
import ai.mantik.elements.CombinerDefinition
import ai.mantik.testutils.TestBase
import akka.util.ByteString

class ScalaFnDefinitionSpec extends TestBase {
  "header conversion" should "work" in {
    val definition = ScalaFnDefinition(
      ScalaFnType.RowMapperType,
      input = Vector(FundamentalType.Int32),
      output = Vector(FundamentalType.Float32),
      payload = ByteString(1, 2, 3)
    )
    val expectedHeader = ScalaFnHeader(
      combiner = CombinerDefinition(
        bridge = ScalaFnHeader.bridgeName,
        Vector(FundamentalType.Int32),
        Vector(FundamentalType.Float32)
      ),
      fnType = ScalaFnType.RowMapperType
    )
    definition.asHeader shouldBe expectedHeader
    definition.asHeader.toMantikHeader.toJsonValue.as[ScalaFnHeader] shouldBe Right(expectedHeader)
  }
}
