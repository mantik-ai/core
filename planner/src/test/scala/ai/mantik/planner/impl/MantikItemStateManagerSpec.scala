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
package ai.mantik.planner.impl

import ai.mantik.ds.element.Bundle
import ai.mantik.elements.ItemId
import ai.mantik.planner.{DataSet, MantikItemState}
import ai.mantik.testutils.TestBase

class MantikItemStateManagerSpec extends TestBase {

  trait Env {
    val manager = new MantikItemStateManager

    val itemId1 = ItemId.generate()
    val itemId2 = ItemId.generate()

    val state1 = MantikItemState(
      nameStored = true
    )
  }

  "get and set" should "work" in new Env {
    manager.get(itemId1) shouldBe None
    manager.set(itemId1, state1)
    manager.get(itemId1) shouldBe Some(state1)
    manager.get(itemId2) shouldBe None
  }

  "update" should "work" in new Env {
    manager.set(itemId1, state1)
    val expected = state1.copy(payloadFile = Some("foo"))
    manager.update(itemId1, _.copy(payloadFile = Some("foo"))) shouldBe Some(expected)
    manager.get(itemId1) shouldBe Some(expected)

    manager.update(itemId2, _.copy(payloadFile = Some("bar"))) shouldBe None
    manager.get(itemId2) shouldBe None
  }

  "updateOrFresh" should "work" in new Env {
    val found = manager.updateOrFresh(itemId1, _.copy(cacheFile = Some("boom")))
    val got = manager.get(itemId1).get
    got shouldBe found
    got shouldBe MantikItemState(cacheFile = Some("boom"))
    val updated = manager.updateOrFresh(itemId1, _.copy(payloadFile = Some("bar")))
    val gotNow = manager.get(itemId1).get
    gotNow shouldBe updated
    gotNow shouldBe MantikItemState(cacheFile = Some("boom"), payloadFile = Some("bar"))
  }

  "upsert" should "work" in new Env {
    val item1 = DataSet.literal(Bundle.fundamental(1))
    manager.upsert(item1, _.copy(payloadFile = Some("baz"))) shouldBe MantikItemState
      .initializeFromSource(item1.source)
      .copy(
        payloadFile = Some("baz")
      )
    val expected = MantikItemState.initializeFromSource(item1.source).copy(nameStored = true, payloadFile = Some("baz"))
    manager.upsert(item1, _.copy(nameStored = true)) shouldBe expected
    manager.get(item1.itemId) shouldBe Some(expected)
    manager.get(itemId2) shouldBe None
  }

  "getOrInit" should "work" in new Env {
    val item1 = DataSet.literal(Bundle.fundamental(1))
    val item2 = DataSet.literal(Bundle.fundamental(2))
    manager.set(item1.itemId, state1)
    manager.getOrInit(item1) shouldBe state1
    manager.getOrInit(item2) shouldBe MantikItemState.initializeFromSource(item2.source)
  }

  "getOrDefault" should "work" in new Env {
    val item1 = DataSet.literal(Bundle.fundamental(1))
    manager.getOrDefault(item1) shouldBe MantikItemState.initializeFromSource(item1.source)
    manager.get(item1.itemId) shouldBe None
    manager.set(item1.itemId, state1)
    manager.getOrDefault(item1) shouldBe state1
    manager.get(item1.itemId) shouldBe Some(state1)
  }
}
