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
package ai.mantik.componently.collections

import ai.mantik.testutils.TestBase
import io.circe.syntax._

class DiGraphSpec extends TestBase {

  val sample = DiGraph[Int, String, String](
    nodes = Map(
      1 -> "Alice",
      2 -> "Bob",
      3 -> "Charly"
    ),
    links = Vector(
      DiLink(1, 2, "Sends a message"),
      DiLink(3, 2, "Spies on")
    )
  )

  it should "serialize well" in {
    sample.asJson.as[DiGraph[Int, String, String]].forceRight shouldBe sample
  }
}
