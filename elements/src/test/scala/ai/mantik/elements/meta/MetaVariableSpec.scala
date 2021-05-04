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
package ai.mantik.elements.meta

import ai.mantik.ds.element.Bundle
import ai.mantik.testutils.TestBase
import io.circe.Json
import io.circe.syntax._

class MetaVariableSpec extends TestBase {

  it should "serialize fine" in {
    val m = MetaVariable(
      "foo",
      Bundle.fundamental(100),
      fix = true
    )
    m.asJson shouldBe Json.obj(
      "name" -> Json.fromString("foo"),
      "value" -> Json.fromInt(100),
      "type" -> Json.fromString("int32"),
      "fix" -> Json.fromBoolean(true)
    )

    m.asJson.as[MetaVariable] shouldBe Right(m)
  }
}
