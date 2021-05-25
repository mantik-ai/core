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
package ai.mantik.bridge.scalafn.payload

import ai.mantik.testutils.TestBase

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream }

class SimpleSerializableFunctionSpec extends TestBase {

  it should "be possible to use a simple serialized function" in {
    val fn: Int => Int = { i =>
      println(s"Got ${i}")
      i + 1
    }

    val byteArrayOutputStream = new ByteArrayOutputStream()

    val objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)
    objectOutputStream.writeObject(fn)
    objectOutputStream.close()

    byteArrayOutputStream.close()
    val bytes = byteArrayOutputStream.toByteArray
    println(s"Bytes size: ${bytes.length}")

    val back = new ObjectInputStream(
      new ByteArrayInputStream(bytes)
    )
    val o = back.readObject()
    val casted = o.asInstanceOf[Int => Int]
    casted(5) shouldBe 6
  }
}
