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
import org.msgpack.core.MessagePack

class MessagePackJsonSupportSpec extends TestBase {

  val samples = Seq(
    "1",
    "432534058345884554",
    "-432534058345884554",
    "null",
    "1.5",
    "-1.5",
    "false",
    """
      |"Hello World"
    """.stripMargin,
    """
      |{"Hello": true}
    """.stripMargin,
    """
      |[1,2,3,4,5]
    """.stripMargin,
    """
      |[1,"Hello", {"Hello": "World", "Deep": {"Deeper": "World"}}, [true, false, "Boom \" Boom"], null]
    """.stripMargin
  )

  it should "serialize and back for all types" in {
    for {
      sample <- samples
    } {
      val parsed = CirceJson.forceParseJson(sample)
      withClue(s"It should work for ${parsed}") {
        val result = MessagePackJsonSupport.toMessagePackBytes(parsed)
        val unpacker = MessagePack.newDefaultUnpacker(result.toArray)
        val json = MessagePackJsonSupport.readJsonToMessagePack(unpacker)
        json shouldBe parsed
        unpacker.hasNext shouldBe false

        val directParsed = MessagePackJsonSupport.fromMessagePackBytes(result)
        directParsed shouldBe parsed
      }
    }
  }

}
