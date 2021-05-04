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

import ai.mantik.ds.testutil.{GlobalAkkaSupport, TestBase}
import akka.stream.scaladsl.Source
import akka.util.ByteString

class ByteSkipperSpec extends TestBase with GlobalAkkaSupport {

  trait Env {
    val skipper = ByteSkipper.make(2)
  }

  it should "do nothing if there there is no input" in new Env {
    val input = Source(
      Vector()
    )
    collectSource(input.via(skipper)) shouldBe empty
  }

  it should "do nothing if there are not enough bytes" in new Env {
    val input = Source(
      Vector(ByteString(1))
    )
    collectSource(input.via(skipper)) shouldBe empty
  }

  it should "skip the first bytes" in new Env {
    val input = Source(
      Vector(
        ByteString(1),
        ByteString(2, 3, 4),
        ByteString(5, 6)
      )
    )
    collectSource(input.via(skipper)) shouldBe Seq(
      ByteString(3, 4),
      ByteString(5, 6)
    )
  }

  it should "work if there are multiple elements to skip" in new Env {
    val input = Source(
      Vector(
        ByteString(1),
        ByteString(2, 3, 4),
        ByteString(5, 6)
      )
    )
    collectSource(input.via(ByteSkipper.make(5))) shouldBe Seq(
      ByteString(6)
    )
  }

}
