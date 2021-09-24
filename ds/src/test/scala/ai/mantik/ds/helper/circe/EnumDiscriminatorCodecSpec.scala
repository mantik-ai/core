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

import ai.mantik.ds.helper.circe.EnumDiscriminatorCodec.UnregisteredElementException
import ai.mantik.ds.testutil.TestBase
import io.circe.Json
import io.circe.syntax._

class EnumDiscriminatorCodecSpec extends TestBase {

  sealed trait Example
  case object Blue extends Example
  case object Red extends Example
  case object Green extends Example

  case object BadUnregeristered extends Example

  implicit object codec
      extends EnumDiscriminatorCodec[Example](
        Seq(
          "blue" -> Blue,
          "red" -> Red,
          "green" -> Green
        )
      )

  it should "decode and encode them all" in {
    for (x <- Seq(Blue, Red, Green)) {
      (x: Example).asJson.as[Example].forceRight shouldBe x
    }
  }

  it should "encode them nice" in {
    (Blue: Example).asJson shouldBe Json.fromString("blue")
  }

  it should "handle encoding errors" in {
    intercept[UnregisteredElementException] {
      (BadUnregeristered: Example).asJson
    }
  }

  it should "handle decoding errors" in {
    Json.fromString("unknown").as[Example].forceLeft
    Json.fromBoolean(true).as[Example].forceLeft
  }

  it should "provide simple serializers" in {
    for (x <- Seq(Blue, Red, Green)) {
      codec.stringToElement(codec.elementToString(x)) shouldBe Some(x)
    }
  }
}
