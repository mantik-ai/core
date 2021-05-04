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
package ai.mantik.planner

import ai.mantik.ds.element.Bundle
import ai.mantik.testutils.TestBase
import io.circe.{Decoder, Encoder}
import io.circe.syntax._

class ActionSpec extends TestBase {

  it should "be serializable" in {
    val ds = DataSet.literal(Bundle.fundamental(5))
    for {
      action <- Seq(
        ds.push(),
        ds.save(),
        ds.fetch,
        Action.Deploy(ds, Some("foo"), Some("bar"))
      )
    } {
      val serialized = (action: Action[_]).asJson
      val back = serialized.as[Action[_]]
      back shouldBe Right(action)
    }
  }

  it should "serialize responses" in {
    def test[T](value: T)(implicit codec: Encoder[T], decoder: Decoder[T]): Unit = {
      value.asJson.as[T] shouldBe Right(value)
    }
    test((): Unit)
    test(Bundle.fundamental(10): Bundle)
    test(DeploymentState("foo", "mnp://intern1", Some("http://external1")))
  }
}
