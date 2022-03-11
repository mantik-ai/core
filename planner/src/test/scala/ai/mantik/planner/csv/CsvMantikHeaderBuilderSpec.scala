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
package ai.mantik.planner.csv

import ai.mantik.ds.{FundamentalType, TabularData}
import ai.mantik.planner.BuiltInItems
import ai.mantik.testutils.TestBase
import io.circe.Json
import io.circe.syntax._

class CsvMantikHeaderBuilderSpec extends TestBase {
  "convert" should "work" in {
    val url = Some("http://example.com/foo.csv")
    val dataType = TabularData(
      "x" -> FundamentalType.Int32
    )
    val options = CsvOptions(
      comma = Some("|"),
      skipHeader = Some(true),
      comment = Some("#")
    )
    val converted = CsvMantikHeaderBuilder(url, dataType, options).convert
    val asJson = converted.toJsonValue

    asJson shouldBe Json.obj(
      "bridge" -> BuiltInItems.CsvBridgeName.asJson,
      "type" -> dataType.asJson,
      "kind" -> "dataset".asJson,
      "options" -> Json.obj(
        "comma" -> "|".asJson,
        "skipHeader" -> true.asJson,
        "comment" -> "#".asJson
      ),
      "url" -> url.get.asJson
    )
  }
}
