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
import io.circe.syntax._
import io.circe.{Json, JsonObject, parser}

class MetaJsonSpec extends TestBase {

  val sample = parser
    .parse(
      """
        |{
        | "metaVariables": [
        |   {
        |     "name": "foo",
        |     "type": "int32",
        |     "value": 5,
        |     "fix": true
        |   },
        |   {
        |     "name": "bar",
        |     "type": "bool",
        |     "value": true,
        |     "fix": false
        |   },
        |   {
        |     "name": "zzz",
        |     "type": "int32",
        |     "value": 100,
        |     "fix": false
        |   }
        | ],
        | "other": "code",
        | "lala": "${foo}",
        | "lulu": "${bar}"
        |}
    """.stripMargin
    )
    .right
    .getOrElse(fail)

  lazy val parsed = sample.as[MetaJson].getOrElse(fail)

  it should "serialize well" in {
    parsed.metaVariables shouldBe List(
      MetaVariable("foo", Bundle.fundamental(5), fix = true),
      MetaVariable("bar", Bundle.fundamental(true)),
      MetaVariable("zzz", Bundle.fundamental(100))
    )
    parsed.sourceJson shouldBe JsonObject(
      "other" -> Json.fromString("code"),
      "lala" -> Json.fromString("${foo}"),
      "lulu" -> Json.fromString("${bar}")
    )
    parsed.asJson shouldBe sample
  }

  it should "be applicable" in {
    parsed.applied shouldBe Right(
      JsonObject(
        "other" -> Json.fromString("code"),
        "lala" -> Json.fromInt(5),
        "lulu" -> Json.fromBoolean(true)
      )
    )
  }

  it should "not care if the element is missing" in {
    val value = Json.obj(
      "foo" -> Json.fromString("bar")
    )
    value.as[MetaJson].right.getOrElse(fail).asJson shouldBe value
    val value2 = Json.obj(
      "foo" -> Json.fromString("bar"),
      "metaVariables" -> Json.arr()
    )
    value2.as[MetaJson].right.getOrElse(fail).asJson shouldBe value2
  }

  "withMetaValues" should "allow new values" in {
    parsed.withMetaValues() shouldBe parsed // no change
    val updated = parsed.withMetaValues(
      "bar" -> Bundle.fundamental(false)
    )
    updated.metaVariables shouldBe List(
      MetaVariable("foo", Bundle.fundamental(5), fix = true),
      MetaVariable("bar", Bundle.fundamental(false), fix = false),
      MetaVariable("zzz", Bundle.fundamental(100))
    )
  }

  it should "throw if a value is fix" in {
    intercept[MetaVariableException] {
      parsed.withMetaValues("foo" -> Bundle.fundamental(6))
    }.getMessage should include("fix")
  }

  it should "throw if a type is wrong" in {
    intercept[MetaVariableException] {
      parsed.withMetaValues("bar" -> Bundle.fundamental("invalid"))
    }.getMessage should include("Invalid type")
  }

  it should "throw if a value is missing" in {
    intercept[MetaVariableException] {
      parsed.withMetaValues("boom" -> Bundle.fundamental(100))
    }.getMessage should include("not found")
  }

  it should "try to adapt types automatically" in {
    val got = parsed.withMetaValues("zzz" -> Bundle.fundamental(300.0))
    got.metaVariable("zzz") shouldBe Some(MetaVariable("zzz", Bundle.fundamental(300))) // automatically casted
  }
}
