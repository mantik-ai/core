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
import io.circe._
import io.circe.syntax._

class DiscriminatorDependentCodecSpec extends TestBase {

  trait Base
  case class Foo(name: String) extends Base
  case class Bar(age: Int) extends Base

  object Base extends DiscriminatorDependentCodec[Base] {
    override val subTypes = Seq(
      makeSubType[Foo]("foo", isDefault = true),
      makeSubType[Bar]("bar")
    )
  }

  val foo = Foo("Alice"): Base
  val bar = Bar(42): Base

  it should "encode and decode" in {
    foo.asJson.as[Base] shouldBe Right(foo)
    bar.asJson.as[Base] shouldBe Right(bar)
  }

  it should "add the kind" in {
    foo.asJson shouldBe Json.obj(
      "name" -> Json.fromString("Alice"),
      "kind" -> Json.fromString("foo")
    )
    bar.asJson shouldBe Json.obj(
      "age" -> Json.fromInt(42),
      "kind" -> Json.fromString("bar")
    )
  }

  it should "decode default values" in {
    Json
      .obj(
        "name" -> Json.fromString("bum")
      )
      .as[Base] shouldBe Right(Foo("bum"))
  }

}
