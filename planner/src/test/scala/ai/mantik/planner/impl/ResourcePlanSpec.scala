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
package ai.mantik.planner.impl

import ai.mantik.ds.FundamentalType.Int32
import ai.mantik.ds.element.{Bundle, Primitive, SingleElement}
import ai.mantik.executor.model.docker.Container
import ai.mantik.planner.graph.{Graph, Link, Node, NodePort, NodePortRef}
import ai.mantik.planner.{PlanFileReference, PlanNodeService, PlanOp}
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
          "SomeType"
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
          "SomeType"
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
          inputs = Vector(NodePort("1i1")),
          outputs = Vector(NodePort("1o1"), NodePort("1o2"))
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
            NodePort("1i1"),
            NodePort("1i2")
          ),
          outputs = Vector(
            NodePort("1o1")
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
          "2o1"
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
              NodePort("1i1"),
              NodePort("1i2")
            ),
            outputs = Vector(
              NodePort("1o1")
            )
          ),
          "2" -> Node.source(
            PlanNodeService.DockerContainer(Container("bar"), None, TestItems.dataSet1),
            "2o1"
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
              NodePort("1i1")
            ),
            outputs = Vector(
              NodePort("1o1"),
              NodePort("1o2")
            )
          ),
          "2" -> Node(
            PlanNodeService.DockerContainer(Container("bar"), None, TestItems.dataSet1),
            outputs = Vector(
              NodePort("2o1")
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
