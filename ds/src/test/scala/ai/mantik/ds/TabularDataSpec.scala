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
package ai.mantik.ds

import ai.mantik.ds.testutil.TestBase

import scala.collection.immutable.ListMap

class TabularDataSpec extends TestBase {

  it should "be build able from name values" in {
    TabularData(
      "foo" -> FundamentalType.Int8,
      "bar" -> FundamentalType.BoolType
    ) shouldBe TabularData(
      columns = ListMap(
        "foo" -> FundamentalType.Int8,
        "bar" -> FundamentalType.BoolType
      )
    )
  }

  it should "know it's order" in {
    val tab = TabularData(
      "foo" -> FundamentalType.Int8,
      "bar" -> FundamentalType.BoolType,
      "buz" -> FundamentalType.StringType
    )
    tab.lookupColumnIndex("foo") shouldBe Some(0)
    tab.lookupColumnIndex("bar") shouldBe Some(1)
    tab.lookupColumnIndex("buz") shouldBe Some(2)
    tab.lookupColumnIndex("unknown") shouldBe None
  }
}
