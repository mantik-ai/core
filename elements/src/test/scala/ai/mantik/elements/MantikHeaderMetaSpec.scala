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
package ai.mantik.elements

import ai.mantik.testutils.TestBase

class MantikHeaderMetaSpec extends TestBase {

  it should "figure out the mantikId" in {
    MantikHeaderMeta().id shouldBe None
    MantikHeaderMeta(
      name = Some("foo")
    ).id shouldBe Some(NamedMantikId("foo"))

    MantikHeaderMeta(
      account = Some("mantik"),
      name = Some("foo")
    ).id shouldBe Some(NamedMantikId("mantik/foo"))

    val bad = MantikHeaderMeta(
      account = Some("mantik"),
      name = Some("mantik/foo")
    )
    bad.id.get.violations shouldNot be(empty)
  }

  it should "take over the id" in {
    val base = MantikHeaderMeta()
    val full = NamedMantikId("account/foo:1234")
    base.withId(full).id shouldBe Some(full)

    val simple = NamedMantikId("bar")

    val withSimple = base.withId(simple)
    withSimple.id shouldBe Some(simple)
    withSimple.account shouldBe empty
    withSimple.version shouldBe empty
  }
}
