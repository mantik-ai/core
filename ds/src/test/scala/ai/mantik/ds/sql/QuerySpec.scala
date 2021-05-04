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
package ai.mantik.ds.sql

import ai.mantik.ds.{FundamentalType, TabularData}
import ai.mantik.testutils.TestBase

class QuerySpec extends TestBase {

  val dataType = TabularData("x" -> FundamentalType.Int32)

  "Union.flat" should "work" in {
    val input1 = AnonymousInput(dataType, 0)
    val input2 = AnonymousInput(dataType, 1)
    val input3 = AnonymousInput(dataType, 2)
    val input4 = AnonymousInput(dataType, 3)
    Union(
      input1,
      input2,
      true
    ).flat shouldBe Vector(
      input1,
      input2
    )

    Union(
      Union(
        input1,
        input2,
        true
      ),
      input3,
      true
    ).flat shouldBe Vector(input1, input2, input3)

    Union(
      Union(
        Union(
          input1,
          input2,
          true
        ),
        input3,
        true
      ),
      input4,
      true
    ).flat shouldBe Vector(input1, input2, input3, input4)

    Union(
      Union(
        input1,
        input2,
        false
      ),
      input3,
      true
    ).flat shouldBe Vector(Union(input1, input2, false), input3)

    Union(
      Union(
        input1,
        input2,
        true
      ),
      input3,
      false
    ).flat shouldBe Vector(Union(input1, input2, true), input3)
  }
}
