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

class RenderableSpec extends TestBase {

  it should "render leafs nicely" in {
    val leaf = Renderable.Leaf("Foo")
    leaf.renderAsString shouldBe "Foo"
  }

  it should "render trees nicely" in {
    val tree = Renderable.SubTree(
      Vector(
        Renderable.Leaf("Bim"),
        Renderable.Leaf("Bum"),
        Renderable.Leaf("Bim")
      ),
      prefix = "- "
    )
    tree.renderAsString shouldBe
      """- Bim
        |- Bum
        |- Bim""".stripMargin
  }

  it should "render sub trees nicely" in {
    val tree = Renderable.SubTree(
      Vector(
        Renderable.Leaf("first"),
        Renderable.SubTree(
          Vector(
            Renderable.Leaf("a"),
            Renderable.Leaf("b")
          ),
          prefix = "+ "
        ),
        Renderable.SubTree(Vector.empty, "*"),
        Renderable.Leaf("last")
      )
    )
    tree.renderAsString shouldBe
      """- first
        |- + a
        |  + b
        |- <empty>
        |- last""".stripMargin
  }

  it should "render trees with headlines" in {
    val tree = Renderable.SubTree(
      Vector(
        Renderable.Leaf("A"),
        Renderable.Leaf("B")
      ),
      prefix = "- ",
      title = Some("Big Itemization")
    )
    tree.renderAsString shouldBe
      """Big Itemization
        |- A
        |- B""".stripMargin
  }
}
