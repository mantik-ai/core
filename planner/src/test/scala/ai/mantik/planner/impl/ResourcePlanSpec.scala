package ai.mantik.planner.impl

import ai.mantik.ds.FundamentalType.Int32
import ai.mantik.ds.element.{Bundle, Primitive, SingleElement}
import ai.mantik.executor.model._
import ai.mantik.executor.model.docker.Container
import ai.mantik.planner.{PlanFileReference, PlanNodeService, PlanOp}
import ai.mantik.testutils.TestBase

class ResourcePlanSpec extends TestBase {

  // Some fake plans to simplify testing
  val lit = Bundle(Int32, Vector(SingleElement(Primitive(1))))
  val pusher  = PlanOp.PushBundle(lit, PlanFileReference(1))
  val pusher2 = PlanOp.PushBundle(lit, PlanFileReference(2))

  val algorithm = ResourcePlan (
    pre = PlanOp.seq(pusher),
    graph = Graph (
      Map(
        "1" -> Node(
          PlanNodeService.DockerContainer(Container("foo"), None, TestItems.algorithm1),
          resources = Map (
            "inout" -> NodeResource(ResourceType.Transformer)
          )
        )
      )
    ),
    inputs = Seq(
      NodeResourceRef("1", "inout")
    ),
    outputs = Seq(
      NodeResourceRef("1", "inout")
    )
  )

  val multiOutput = ResourcePlan(
    pre = PlanOp.seq(pusher),
    graph = Graph (
      Map(
        "1" -> Node(
          PlanNodeService.DockerContainer(Container("learner"), None, TestItems.learning1),
          resources = Map (
            "in1" -> NodeResource(ResourceType.Sink),
            "out1" -> NodeResource(ResourceType.Source),
            "out2" -> NodeResource(ResourceType.Source)
          )
        )
      )
    ),
    inputs = Seq(
      NodeResourceRef("1", "in1")
    ),
    outputs = Seq(
      NodeResourceRef("1", "out1"),
      NodeResourceRef("1", "out2")
    )
  )

  val multiInput = ResourcePlan (
    pre = PlanOp.seq(pusher),
    graph = Graph (
      Map(
        "1" -> Node(
          PlanNodeService.DockerContainer(Container("learner"), None, TestItems.learning1),
          resources = Map (
            "in1" -> NodeResource(ResourceType.Sink),
            "in2" -> NodeResource(ResourceType.Sink),
            "out" -> NodeResource(ResourceType.Source)
          )
        )
      )
    ),
    inputs = Seq(
      NodeResourceRef("1", "in1"),
      NodeResourceRef("1", "in2"),
    ),
    outputs = Seq(
      NodeResourceRef("1", "out"),
    )
  )

  val dataset = ResourcePlan (
    pre = PlanOp.seq(pusher2),
    graph = Graph (
      Map (
        "2" -> Node(
          PlanNodeService.DockerContainer(Container("bar"), None, TestItems.dataSet1),
          resources = Map (
            "out" -> NodeResource(ResourceType.Source)
          )
        )
      )
    ),
    outputs = Seq(
      NodeResourceRef("2", "out")
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
    intercept[IllegalArgumentException]{
      multiOutput.projectOutput(2)
    }
  }

  "application" should "apply an argument to a plan" in {
    algorithm.application(dataset) shouldBe ResourcePlan(
      pre = PlanOp.seq(pusher, pusher2),
      graph = Graph(
        Map (
          "1" -> Node(
            PlanNodeService.DockerContainer(Container("foo"), None, TestItems.algorithm1),
            resources = Map (
              "inout" -> NodeResource(ResourceType.Transformer)
            )
          ),
          "2" -> Node(
            PlanNodeService.DockerContainer(Container("bar"), None, TestItems.dataSet1),
            resources = Map (
              "out" -> NodeResource(ResourceType.Source)
            )
          )
        ),
        links = Seq(
          Link(NodeResourceRef("2", "out"), NodeResourceRef("1", "inout"))
        )
      ),
      inputs = Seq.empty,
      outputs = Seq(NodeResourceRef("1", "inout"))
    )
  }

  it should "work when not all inputs are used" in {
    multiInput.application(dataset) shouldBe ResourcePlan (
      pre = PlanOp.seq(pusher, pusher2),
      graph = Graph(
        Map (
          "1" -> Node(
            PlanNodeService.DockerContainer(Container("learner"), None, TestItems.learning1),
            resources = Map (
              "in1" -> NodeResource(ResourceType.Sink),
              "in2" -> NodeResource(ResourceType.Sink),
              "out" -> NodeResource(ResourceType.Source)
            )
          ),
          "2" -> Node(
            PlanNodeService.DockerContainer(Container("bar"), None, TestItems.dataSet1),
            resources = Map (
              "out" -> NodeResource(ResourceType.Source)
            )
          )
        ),
        links = Seq(
          Link(NodeResourceRef("2", "out"), NodeResourceRef("1", "in1"))
        )
      ),
      inputs = Seq(NodeResourceRef("1", "in2")),
      outputs = Seq(NodeResourceRef("1", "out"))
    )
  }

  it should "work when not all outputs are used" in {
    multiOutput.application(dataset) shouldBe ResourcePlan (
      pre = PlanOp.seq(pusher, pusher2),
      graph = Graph(
        Map (
          "1" -> Node(
            PlanNodeService.DockerContainer(Container("learner"), None, TestItems.learning1),
            resources = Map (
              "in1" -> NodeResource(ResourceType.Sink),
              "out1" -> NodeResource(ResourceType.Source),
              "out2" -> NodeResource(ResourceType.Source)
            )
          ),
          "2" -> Node(
            PlanNodeService.DockerContainer(Container("bar"), None, TestItems.dataSet1),
            resources = Map (
              "out" -> NodeResource(ResourceType.Source)
            )
          )
        ),
        links = Seq(
          Link(NodeResourceRef("2", "out"), NodeResourceRef("1", "in1"))
        )
      ),
      inputs = Nil,
      outputs = Seq(
        NodeResourceRef("1", "out1"),
        NodeResourceRef("1", "out2")
      )
    )
  }

}
