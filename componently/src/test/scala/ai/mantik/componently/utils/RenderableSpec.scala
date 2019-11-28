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
