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
