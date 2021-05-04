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
package ai.mantik.ds.helper

import ai.mantik.ds.testutil.TestBase

class TableFormatterSpec extends TestBase {

  "format" should "not fail on empty" in {
    TableFormatter.format(Nil, Nil) shouldBe ""
  }

  it should "render nice 1 tables" in {
    val rendered = TableFormatter.format(
      Seq("ABC"),
      Seq(Seq("Hello World"))
    )
    val expected =
      """|ABC        |
        @|-----------|
        @|Hello World|
        @""".stripMargin('@')

    rendered shouldBe expected
  }

  it should "render nice longer tables" in {
    val rendered = TableFormatter.format(
      Seq("Long Header", "B"),
      Seq(
        Seq("X1", "X2"),
        Seq("ergerg", "g4gergnrkengn")
      )
    )
    val expected =
      """|Long Header|B            |
        @|-----------|-------------|
        @|X1         |X2           |
        @|ergerg     |g4gergnrkengn|
        @""".stripMargin('@')
    rendered shouldBe expected
  }

  it should "crash on length violations" in {
    intercept[IllegalArgumentException] {
      TableFormatter.format(Seq("A", "B"), Seq(Seq("C", "D"), Seq("E")))
    }
    intercept[IllegalArgumentException] {
      TableFormatter.format(Seq("A", "B"), Seq(Seq("C", "D"), Seq("E", "F", "G")))
    }
  }
}
