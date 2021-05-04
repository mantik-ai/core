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
package ai.mantik.planner.graph

import ai.mantik.testutils.TestBase

class GraphSpec extends TestBase {

  val graph1 = Graph(
    nodes = Map(
      "1" -> Node(
        "Hello",
        inputs = Vector(
          NodePort("A"),
          NodePort("B")
        ),
        outputs = Vector(
          NodePort("C")
        )
      ),
      "2" -> Node(
        "World",
        inputs = Vector(
          NodePort("C")
        )
      )
    ),
    links = Vector(
      Link(NodePortRef("Hello", 0), NodePortRef("World", 0))
    )
  )

  it should "support basic queries" in {
    graph1.resolveInput(NodePortRef("X", 0)) shouldBe None
    graph1.resolveInput(NodePortRef("1", 1)) shouldBe Some(graph1.nodes("1"), NodePort("B"))
    graph1.resolveOutput(NodePortRef("2", 0)) shouldBe None
    graph1.resolveOutput(NodePortRef("1", 0)) shouldBe Some(graph1.nodes("1"), NodePort("C"))
  }

}
