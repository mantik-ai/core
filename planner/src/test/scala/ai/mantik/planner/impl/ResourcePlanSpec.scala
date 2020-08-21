package ai.mantik.planner.impl

import ai.mantik.ds.FundamentalType.Int32
import ai.mantik.ds.element.{ Bundle, Primitive, SingleElement }
import ai.mantik.executor.model.docker.Container
import ai.mantik.planner.graph.{ Graph, Link, Node, NodePort, NodePortRef }
import ai.mantik.planner.{ PlanFileReference, PlanNodeService, PlanOp }
import ai.mantik.testutils.TestBase

class ResourcePlanSpec extends TestBase {

  // Some fake plans to simplify testing
  val lit = Bundle(Int32, Vector(SingleElement(Primitive(1))))
  val pusher = PlanOp.StoreBundleToFile(lit, PlanFileReference(1))
  val pusher2 = PlanOp.StoreBundleToFile(lit, PlanFileReference(2))

  val algorithm = ResourcePlan(
    pre = PlanOp.seq(pusher),
    graph = Graph(
      Map(
        "1" -> Node.transformer(
          PlanNodeService.DockerContainer(Container("foo"), None, TestItems.algorithm1),
          Some("SomeType")
        )
      )
    ),
    inputs = Seq(
      NodePortRef("1", 0)
    ),
    outputs = Seq(
      NodePortRef("1", 0)
    )
  )

  val algorithm2 = ResourcePlan(
    pre = PlanOp.seq(pusher2),
    graph = Graph(
      Map(
        "2" -> Node.transformer(
          PlanNodeService.DockerContainer(Container("bar"), None, TestItems.algorithm1),
          Some("SomeType")
        )
      )
    ),
    inputs = Seq(
      NodePortRef("2", 0)
    ),
    outputs = Seq(
      NodePortRef("2", 0)
    )
  )

  val multiOutput = ResourcePlan(
    pre = PlanOp.seq(pusher),
    graph = Graph(
      Map(
        "1" -> Node(
          PlanNodeService.DockerContainer(Container("learner"), None, TestItems.learning1),
          inputs = Vector(NodePort(None)),
          outputs = Vector(NodePort(None), NodePort(None))
        )
      )
    ),
    inputs = Seq(
      NodePortRef("1", 0)
    ),
    outputs = Seq(
      NodePortRef("1", 0),
      NodePortRef("1", 1)
    )
  )

  val multiInput = ResourcePlan(
    pre = PlanOp.seq(pusher),
    graph = Graph(
      Map(
        "1" -> Node(
          PlanNodeService.DockerContainer(Container("learner"), None, TestItems.learning1),
          inputs = Vector(
            NodePort(None),
            NodePort(None)
          ),
          outputs = Vector(
            NodePort(None)
          )
        )
      )
    ),
    inputs = Seq(
      NodePortRef("1", 0),
      NodePortRef("1", 1)
    ),
    outputs = Seq(
      NodePortRef("1", 0)
    )
  )

  val dataset = ResourcePlan(
    pre = PlanOp.seq(pusher2),
    graph = Graph(
      Map(
        "2" -> Node.source(
          PlanNodeService.DockerContainer(Container("bar"), None, TestItems.dataSet1),
          None
        )
      )
    ),
    outputs = Seq(
      NodePortRef("2", 0)
    )
  )

  "prependOp" should "prepend a plan" in {
    algorithm.prependOp(pusher2) shouldBe algorithm.copy(
      pre = PlanOp.seq(pusher2, pusher)
    )
    algorithm.copy(pre = PlanOp.Empty).prependOp(pusher2) shouldBe algorithm.copy(
      pre = pusher2
    )
    algorithm.copy(pre = PlanOp.seq(pusher, pusher2)).prependOp(pusher2) shouldBe algorithm.copy(
      pre = PlanOp.seq(pusher2, pusher, pusher2)
    )
  }

  "project output" should "select a single output" in {
    multiOutput.projectOutput(0) shouldBe multiOutput.copy(
      outputs = Seq(multiOutput.outputs.head)
    )
    multiOutput.projectOutput(1) shouldBe multiOutput.copy(
      outputs = Seq(multiOutput.outputs(1))
    )
    intercept[IllegalArgumentException] {
      multiOutput.projectOutput(2)
    }
  }

  "application" should "apply an argument to a plan" in {
    algorithm.application(dataset) shouldBe ResourcePlan(
      pre = PlanOp.seq(pusher, pusher2),
      graph = Graph(
        Map(
          "1" -> algorithm.graph.nodes("1"),
          "2" -> dataset.graph.nodes("2")
        ),
        links = Seq(
          Link(NodePortRef("2", 0), NodePortRef("1", 0))
        )
      ),
      inputs = Seq.empty,
      outputs = Seq(NodePortRef("1", 0))
    )
  }

  it should "work when not all inputs are used" in {
    multiInput.application(dataset) shouldBe ResourcePlan(
      pre = PlanOp.seq(pusher, pusher2),
      graph = Graph(
        Map(
          "1" -> Node(
            PlanNodeService.DockerContainer(Container("learner"), None, TestItems.learning1),
            inputs = Vector(
              NodePort(None),
              NodePort(None)
            ),
            outputs = Vector(
              NodePort(None)
            )
          ),
          "2" -> Node.source(
            PlanNodeService.DockerContainer(Container("bar"), None, TestItems.dataSet1),
            None
          )
        ),
        links = Seq(
          Link(NodePortRef("2", 0), NodePortRef("1", 0))
        )
      ),
      inputs = Seq(NodePortRef("1", 1)),
      outputs = Seq(NodePortRef("1", 0))
    )
  }

  it should "work when not all outputs are used" in {
    multiOutput.application(dataset) shouldBe ResourcePlan(
      pre = PlanOp.seq(pusher, pusher2),
      graph = Graph(
        Map(
          "1" -> Node(
            PlanNodeService.DockerContainer(Container("learner"), None, TestItems.learning1),
            inputs = Vector(
              NodePort(None)
            ),
            outputs = Vector(
              NodePort(None),
              NodePort(None)
            )
          ),
          "2" -> Node(
            PlanNodeService.DockerContainer(Container("bar"), None, TestItems.dataSet1),
            outputs = Vector(
              NodePort(None)
            )
          )
        ),
        links = Seq(
          Link(NodePortRef("2", 0), NodePortRef("1", 0))
        )
      ),
      inputs = Nil,
      outputs = Seq(
        NodePortRef("1", 0),
        NodePortRef("1", 1)
      )
    )
  }

  it should "be possible to chain algorithms using applications" in {
    val result = algorithm2.application(algorithm)
    result shouldBe ResourcePlan(
      pre = PlanOp.seq(pusher2, pusher),
      graph = Graph(
        Map(
          "1" -> algorithm.graph.nodes("1"),
          "2" -> algorithm2.graph.nodes("2")
        ),
        links = Seq(
          Link(NodePortRef("1", 0), NodePortRef("2", 0))
        )
      ),
      inputs = Seq(
        NodePortRef("1", 0)
      ),
      outputs = Seq(
        NodePortRef("2", 0)
      )
    )
  }

}
