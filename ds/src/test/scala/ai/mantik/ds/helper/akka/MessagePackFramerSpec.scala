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
package ai.mantik.ds.helper.akka

import ai.mantik.ds.helper.circe.MessagePackJsonSupport
import ai.mantik.ds.testutil.{GlobalAkkaSupport, TestBase}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import io.circe.Json

class MessagePackFramerSpec extends TestBase with GlobalAkkaSupport {

  trait Env {
    val framer = MessagePackFramer.make()

    val chunks = Seq(
      MessagePackJsonSupport.toMessagePackBytes(Json.Null),
      MessagePackJsonSupport.toMessagePackBytes(Json.fromDoubleOrNull(3.1415)),
      MessagePackJsonSupport.toMessagePackBytes(Json.fromInt(5)),
      MessagePackJsonSupport.toMessagePackBytes(Json.fromBoolean(true)),
      MessagePackJsonSupport.toMessagePackBytes(
        Json.obj(
          "foo" -> Json.obj(
            "bar" -> Json.fromString("bar")
          ),
          "baz" -> Json.fromLong(22134325345435L)
        )
      ),
      MessagePackJsonSupport.toMessagePackBytes(
        Json.arr(
          Json.fromInt(1),
          Json.fromInt(2),
          Json.fromInt(3),
          Json.fromInt(4)
        )
      )
    )
    val allTogether = chunks.fold(ByteString.empty)(_ ++ _)
  }

  it should "work for empty streams" in new Env {
    val input = Source.apply[ByteString](
      Vector.empty
    )
    collectSource(input.via(framer)) shouldBe empty
  }

  it should "work for too big chunks" in new Env {
    val input = Source(Vector(allTogether))
    collectSource(input.via(framer)) shouldBe chunks
  }

  it should "work for too small chunks" in new Env {
    val parts = allTogether.indices.map { byte =>
      allTogether.drop(byte).take(1)
    }
    val input = Source.apply(parts)
    collectSource(input.via(framer)) shouldBe chunks
  }
}
