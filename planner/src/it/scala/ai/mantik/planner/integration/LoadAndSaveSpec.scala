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
package ai.mantik.planner.integration

import ai.mantik.ds.element.Bundle
import ai.mantik.elements.NamedMantikId
import ai.mantik.planner.DataSet

class LoadAndSaveSpec extends IntegrationTestBase {

  it should "be possible to load and save an item" in {

    val item = DataSet
      .literal(Bundle.fundamental(100))
      .tag("item1")

    context.execute(item.save())

    val item2 = context.loadDataSet("item1")

    item2.mantikId shouldBe item.mantikId
    item2.itemId shouldBe item.itemId

    withClue("It should be possible to save with another name again") {
      context.execute(item2.tag("other_name").save())

      val item3 = context.loadDataSet("other_name")
      item3.mantikId shouldBe NamedMantikId("other_name")
      item3.itemId shouldBe item.itemId
    }
  }
}
