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
package ai.mantik.bridge.scalafn.cs

import ai.mantik.testutils.TestBase

import java.nio.file.{ Files, Paths }

case class SampleAdder(adder: Int) {
  def apply(i: Int): Int = {
    i + adder
  }
}

case class Sample(f: Int => Int)

object SampleHolder {
  val sample = Sample { i =>
    new SampleAdder(10).apply(5)
  }
}

class RoundTripSpec extends TestBase {
  it should "serialize and deserialize" in {
    val sample = SampleHolder.sample
    sample.f(10) shouldBe 15

    val serializer = new ClosureSerializer()
    val cleaned = Sample(serializer.cleanClosure(sample.f))
    val serialized = serializer.serialize(cleaned)
    try {
      Files.exists(serialized) shouldBe true
      serialized.toString should endWith(".jar")

      val deserializer = new ClosureDeserializer()
      val deserialized = deserializer.deserialize(serialized).asInstanceOf[Sample]

      deserialized.f(10) shouldBe 15
    } finally {
      Files.delete(serialized)
    }

  }
}
