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
package ai.mantik.ds.helper.circe

import ai.mantik.ds.testutil.TestBase
import io.circe.Json
import io.circe.syntax._

class CirceJsonSpec extends TestBase {

  "stripNullValues" should "work" in {
    val x = Json.obj(
      "a" -> true.asJson,
      "b" -> "Hello".asJson,
      "c" -> Json.obj(
        "foo" -> Json.Null,
        "bar" -> Json.arr(
          Json.Null,
          Json.obj(
            "x" -> Json.Null,
            "y" -> 3.asJson,
            "z" -> "35".asJson
          )
        )
      )
    )
    CirceJson.stripNullValues(
      x
    ) shouldBe Json.obj(
      "a" -> true.asJson,
      "b" -> "Hello".asJson,
      "c" -> Json.obj(
        "bar" -> Json.arr(
          Json.Null,
          Json.obj(
            "y" -> 3.asJson,
            "z" -> "35".asJson
          )
        )
      )
    )
  }
}
