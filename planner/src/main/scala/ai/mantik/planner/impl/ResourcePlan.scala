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

import ai.mantik.executor.model._
import ai.mantik.planner.{Plan, PlanNodeService, PlanOp, Planner}
import PlannerGraphOps._
import ai.mantik.componently.utils.Renderable
import ai.mantik.planner
import ai.mantik.planner.graph.{Graph, Link, Node, NodePort, NodePortRef}

/**
  * Describes a way a resource is calculated (a containing part of a graph).
  *
  * @param pre a plan op which must be executed before evaluating the graph
  * @param graph graph to evaluate
  * @param inputs input resources
  * @param outputs output resources
  *
  * Node: resources may be input and output at the same time.
  */
private[impl] case class ResourcePlan(
    pre: PlanOp[Unit] = PlanOp.Empty,
    graph: Graph[PlanNodeService] = Graph.empty,
    inputs: Seq[NodePortRef] = Nil,
    outputs: Seq[NodePortRef] = Nil
) {

  def prependOp(plan: PlanOp[_]): ResourcePlan = {
    copy(
      pre = PlanOp.combine(plan, pre)
    )
  }

  /** Selects a single output. */
  def projectOutput(id: Int): ResourcePlan = {
    require(id >= 0 && id < outputs.length)
    copy(
      outputs = Seq(outputs(id))
    )
  }

  /** Feeds data of the argument into the input of this plan and returns it's result */
  def application(argument: ResourcePlan): ResourcePlan = {
    require(inputs.size >= argument.outputs.size)
    val extraLinks = inputs.zip(argument.outputs).map { case (input, output) =>
      Link(output, input)
    }
    ResourcePlan(
      pre = PlanOp.combine(pre, argument.pre),
      graph = graph.mergeWith(argument.graph).addLinks(extraLinks: _*),
      inputs = argument.inputs ++ inputs.drop(argument.outputs.size),
      outputs = outputs
    )
  }

  def outputResource(id: Int): NodePort = {
    require(id >= 0 && id < outputs.length, "Invalid output id")
    outputResource(outputs(id))
  }

  def outputResource(nodeResourceRef: NodePortRef): NodePort = {
    graph
      .resolveOutput(nodeResourceRef)
      .getOrElse {
        throw new planner.Planner.InconsistencyException(s"Output ${nodeResourceRef} could not be resolved")
      }
      ._2
  }

  def merge(other: ResourcePlan): ResourcePlan = {
    ResourcePlan(
      pre = PlanOp.combine(pre, other.pre),
      graph = graph.mergeWith(other.graph),
      inputs = inputs ++ other.inputs,
      outputs = outputs ++ other.outputs
    )
  }

  override def toString: String = {
    Renderable.renderAsString(this)(ResourcePlan.renderable)
  }
}

object ResourcePlan {
  implicit val renderable = new Renderable[ResourcePlan] {
    override def buildRenderTree(value: ResourcePlan): Renderable.RenderTree = {
      Renderable.keyValueList(
        "ResourcePlan",
        "pre" -> value.pre,
        "graph" -> value.graph,
        "inputs" -> value.inputs,
        "outputs" -> value.outputs
      )
    }
  }

  /** Generates a Resource plan for a single node. */
  def singleNode(nodeId: String, node: Node[PlanNodeService]): ResourcePlan = {
    val graph = Graph(
      nodes = Map(
        nodeId -> node
      )
    )
    ResourcePlan(
      graph = graph,
      inputs = node.inputs.indices.map { port =>
        NodePortRef(nodeId, port)
      },
      outputs = node.outputs.indices.map { port =>
        NodePortRef(nodeId, port)
      }
    )
  }
}
