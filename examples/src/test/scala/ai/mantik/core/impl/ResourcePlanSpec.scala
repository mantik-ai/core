package ai.mantik.core.impl

import ai.mantik.core.Plan
import ai.mantik.ds.FundamentalType.Int32
import ai.mantik.ds.element.{Bundle, Primitive, SingleElement}
import ai.mantik.executor.model._
import ai.mantik.testutils.TestBase

class ResourcePlanSpec extends TestBase {

  // Some fake plans to simplify testing
  val lit = Bundle(Int32, Vector(SingleElement(Primitive(1))))
  val pusher  = Plan.PushBundle(lit, "file1")
  val pusher2 = Plan.PushBundle(lit, "file2")

  val algorithm = ResourcePlan (
    preplan = Plan.seq(pusher),
    graph = Graph (
      Map(
        "1" -> Node(
          ExistingService("foo"),
          resources = Map (
            "inout" -> ResourceType.Transformer
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
    preplan = Plan.seq(pusher),
    graph = Graph (
      Map(
        "1" -> Node(
          ExistingService("learner"),
          resources = Map (
            "in1" -> ResourceType.Sink,
            "out1" -> ResourceType.Source,
            "out2" -> ResourceType.Source
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
    preplan = Plan.seq(pusher),
    graph = Graph (
      Map(
        "1" -> Node(
          ExistingService("learner"),
          resources = Map (
            "in1" -> ResourceType.Sink,
            "in2" -> ResourceType.Sink,
            "out" -> ResourceType.Source
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
    preplan = Plan.seq(pusher2),
    graph = Graph (
      Map (
        "2" -> Node(
          ExistingService("bar"),
          resources = Map (
            "out" -> ResourceType.Source
          )
        )
      )
    ),
    outputs = Seq(
      NodeResourceRef("2", "out")
    )
  )

  "prependPlan" should "prepend a plan" in {
    algorithm.prependPlan(pusher2) shouldBe algorithm.copy(
      preplan = Plan.seq(pusher2, pusher)
    )
    algorithm.copy(preplan = Plan.Empty).prependPlan(pusher2) shouldBe algorithm.copy(
      preplan = pusher2
    )
    algorithm.copy(preplan = Plan.seq(pusher, pusher2)).prependPlan(pusher2) shouldBe algorithm.copy(
      preplan = Plan.seq(pusher2, pusher, pusher2)
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
      preplan = Plan.seq(pusher, pusher2),
      graph = Graph(
        Map (
          "1" -> Node(
            ExistingService("foo"),
            resources = Map (
              "inout" -> ResourceType.Transformer
            )
          ),
          "2" -> Node(
            ExistingService("bar"),
            resources = Map (
              "out" -> ResourceType.Source
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
      preplan = Plan.seq(pusher, pusher2),
      graph = Graph(
        Map (
          "1" -> Node(
            ExistingService("learner"),
            resources = Map (
              "in1" -> ResourceType.Sink,
              "in2" -> ResourceType.Sink,
              "out" -> ResourceType.Source
            )
          ),
          "2" -> Node(
            ExistingService("bar"),
            resources = Map (
              "out" -> ResourceType.Source
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
      preplan = Plan.seq(pusher, pusher2),
      graph = Graph(
        Map (
          "1" -> Node(
            ExistingService("learner"),
            resources = Map (
              "in1" -> ResourceType.Sink,
              "out1" -> ResourceType.Source,
              "out2" -> ResourceType.Source
            )
          ),
          "2" -> Node(
            ExistingService("bar"),
            resources = Map (
              "out" -> ResourceType.Source
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
