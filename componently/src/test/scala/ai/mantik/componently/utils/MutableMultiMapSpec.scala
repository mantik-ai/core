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
package ai.mantik.componently.utils

import ai.mantik.testutils.TestBase

class MutableMultiMapSpec extends TestBase {
  it should "work" in {
    val map = new MutableMultiMap[Int, String]()
    map.get(1) shouldBe Set.empty
    map.add(2, "Hello")
    map.add(2, "World")
    map.get(2) shouldBe Set("Hello", "World")
    map.add(1, "Foo")
    map.remove(2, "Not existing")
    map.get(2) shouldBe Set("Hello", "World")
    map.valueCount(2) shouldBe 2
    map.valueCount(-1) shouldBe 0
    map.keyCount shouldBe 2
    map.remove(1)
    map.get(1) shouldBe empty
    map.keyCount shouldBe 1
    map.remove(2, "Hello")
    map.valueCount(2) shouldBe 1
    map.get(2) shouldBe Set("World")
    map.add(2, "Bar")
    map.remove(2)
    map.add(1, "Buz")
    map.get(1) shouldBe Set("Buz")
    map.clear()
    map.keyCount shouldBe 0
  }
}
