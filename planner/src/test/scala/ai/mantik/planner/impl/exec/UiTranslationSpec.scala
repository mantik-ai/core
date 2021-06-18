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
package ai.mantik.planner.impl.exec

import ai.mantik.ds.FundamentalType
import ai.mantik.ds.FundamentalType.Int32
import ai.mantik.ds.element.{Bundle, Primitive, SingleElement}
import ai.mantik.ds.functional.FunctionType
import ai.mantik.elements.{AlgorithmDefinition, MantikHeader}
import ai.mantik.executor.model.docker.Container
import ai.mantik.planner.graph.{Graph, Link, Node, NodePort, NodePortRef}
import ai.mantik.planner.repository.ContentTypes
import ai.mantik.planner.{Plan, PlanFile, PlanFileReference, PlanNodeService, PlanOp}
import ai.mantik.testutils.TestBase
import ai.mantik.ui.model.OperationId
import akka.util.ByteString

class UiTranslationSpec extends TestBase {

  // Note: the plan doesn't make any sense at all
  val samplePlan = Plan(
    op = PlanOp.seq(
      PlanOp.StoreBundleToFile(Bundle(Int32, Vector(SingleElement(Primitive(1)))), PlanFileReference(1)),
      PlanOp.RunGraph(
        graph = Graph(
          nodes = Map(
            "A" -> Node(
              PlanNodeService.DockerContainer(
                Container(
                  image = "foo",
                  parameters = Seq("a", "b")
                ),
                data = None,
                embeddedData = Some(ContentTypes.MantikBundleContentType -> ByteString(1, 2, 3)),
                mantikHeader = MantikHeader.pure(
                  AlgorithmDefinition(
                    "bridge1",
                    FunctionType(
                      FundamentalType.Int32,
                      FundamentalType.Float32
                    )
                  )
                )
              ),
              outputs = Vector(
                NodePort(ContentTypes.MantikBundleContentType)
              )
            ),
            "B" -> Node(
              PlanNodeService.File(
                PlanFileReference(1)
              ),
              inputs = Vector(
                NodePort(ContentTypes.MantikBundleContentType)
              )
            )
          ),
          links = Seq(
            Link(
              NodePortRef("A", 0),
              NodePortRef("B", 0)
            )
          )
        )
      )
    ),
    files = List(
      PlanFile(
        PlanFileReference(1),
        contentType = ContentTypes.OctetStreamContentType
      )
    ),
    cachedItems = Set.empty
  )

  "translateOperations" should "translate operations" in {
    val ops = UiTranslation.translateOperations(samplePlan)
    ops.size shouldBe 4 // 2x preparation, push and run graph
  }

  "translateRunGraphs" should "translate run graphs" in {
    val graphs = UiTranslation.translateRunGraphs(samplePlan)
    graphs.size shouldBe 1
    graphs.head._1 shouldBe OperationId(Right(List(1)))
    val runGraph = graphs.head._2
    runGraph.nodes.size shouldBe 2
    runGraph.links.size shouldBe 1
  }
}
