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
import io.circe.{Json, parser}

class MetaVariableApplicationSpec extends TestBase {

  val transformation = MetaVariableApplication(
    List(
      MetaVariable("foo", Bundle.fundamental(100)),
      MetaVariable("bar", Bundle.fundamental("Hi"))
    )
  )

  it should "work for a simple use case" in {
    transformation.apply(Json.fromString("${foo}")) shouldBe Right(Json.fromInt(100))
    transformation.apply(Json.fromString("${bar}")) shouldBe Right(Json.fromString("Hi"))
    transformation.apply(Json.fromString("${unknown}")) shouldBe Left(
      "Variable unknown not found"
    )
    transformation.apply(Json.fromString("$${escaped}")) shouldBe Right(Json.fromString("${escaped}"))
  }

  it should "work for a complicated use case" in {
    val json1 = parser
      .parse(
        """
          |{
          | "a": null,
          | "b": 1,
          | "c": false,
          | "d": {
          |   "e": "${foo}",
          |   "h": ["${bar}"]
          | }
          |}
      """.stripMargin
      )
      .right
      .getOrElse(fail)

    val expected = parser
      .parse(
        """
          |{
          | "a": null,
          | "b": 1,
          | "c": false,
          | "d": {
          |   "e": 100,
          |   "h": ["Hi"]
          | }
          |}
      """.stripMargin
      )
      .right
      .getOrElse(fail)

    transformation.apply(json1) shouldBe Right(expected)
  }
}
