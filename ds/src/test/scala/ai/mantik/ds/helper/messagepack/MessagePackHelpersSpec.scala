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
package ai.mantik.ds.helper.messagepack

import ai.mantik.ds.helper.circe.{CirceJson, MessagePackJsonSupport}
import ai.mantik.ds.testutil.TestBase
import akka.util.ByteString

class MessagePackHelpersSpec extends TestBase {

  "consumableBytes" should "work for a simple message" in {
    val message =
      """
        |{
        | "a": 1,
        | "b": 1.4,
        | "c": 1.3,
        | "d": -1.3,
        | "e": true,
        | "f": null,
        | "g": {
        |   "sub1": 1,
        |   "sub2": null
        | },
        | "h": [
        |   1,2,3,4,5,6,7
        | ]
        |}
      """.stripMargin
    val messageJson = CirceJson.forceParseJson(message)
    val messagePack = MessagePackJsonSupport.toMessagePackBytes(messageJson)
    for {
      i <- messagePack.indices
    } {
      val part = messagePack.take(i)
      val result = MessagePackHelpers.consumableBytes(part)
      result shouldBe None
    }
    MessagePackHelpers.consumableBytes(messagePack) shouldBe Some(messagePack.length)
    MessagePackHelpers.consumableBytes(messagePack ++ ByteString(0x1, 0x2, 0x3)) shouldBe Some(messagePack.length)
  }
}
