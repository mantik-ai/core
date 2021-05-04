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

class TrialDependentCodecSpec extends TestBase {

  sealed trait Base

  case class Foo(foo: Int) extends Base
  case class Bar(bar: String) extends Base
  case class Unregistered(boom: String) extends Base // not registered

  implicit val trialCodec = new TrialDependentCodec[Base] {
    override val subTypes = Seq(
      makeSubType[Foo](),
      makeSubType[Bar]()
    )
  }

  def toJson(base: Base): Json = {
    base.asJson
  }

  it should "encode nice" in {
    toJson(Foo(3)) shouldBe Json.obj("foo" -> 3.asJson)
    toJson(Bar("mee")) shouldBe Json.obj("bar" -> "mee".asJson)
  }

  it should "decode and encode" in {
    val samples = Seq(Foo(3), Bar(""), Bar("Hello"))
    samples.foreach(x => toJson(x).as[Base] shouldBe Right(x))
  }

  it should "handle unknown encodes" in {
    intercept[IllegalArgumentException] {
      toJson(Unregistered("Boom"))
    }
  }

  it should "handle unknown json" in {
    Json.obj("unknown" -> 2.asJson).as[Base].isLeft shouldBe true
    Json.fromInt(3).as[Base].isLeft shouldBe true
  }

}
