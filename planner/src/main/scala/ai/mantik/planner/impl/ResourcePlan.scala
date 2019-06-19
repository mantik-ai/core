package ai.mantik.planner.impl

import ai.mantik.executor.model._
import ai.mantik.planner.{ Plan, PlanNodeService, PlanOp, Planner }
import PlannerGraphOps._
import ai.mantik.planner

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
    pre: PlanOp = PlanOp.Empty,
    graph: Graph[PlanNodeService] = Graph.empty,
    inputs: Seq[NodeResourceRef] = Nil,
    outputs: Seq[NodeResourceRef] = Nil
) {

  def prependOp(plan: PlanOp): ResourcePlan = {
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
    val extraLinks = inputs.zip(argument.outputs).map {
      case (input, output) =>
        Link(output, input)
    }
    ResourcePlan(
      pre = PlanOp.combine(pre, argument.pre),
      graph = graph.mergeWith(argument.graph).addLinks(extraLinks: _*),
      inputs = argument.inputs ++ inputs.drop(argument.outputs.size),
      outputs = outputs
    )
  }

  def outputResource(id: Int): NodeResource = {
    require(id >= 0 && id < outputs.length, "Invalid output id")
    outputResource(outputs(id))
  }

  def outputResource(nodeResourceRef: NodeResourceRef): NodeResource = {
    graph.resolveReference(nodeResourceRef).getOrElse {
      throw new planner.Planner.InconsistencyException(s"Output ${nodeResourceRef} could not be resolved")
    }._2
  }

  def merge(other: ResourcePlan): ResourcePlan = {
    ResourcePlan(
      pre = PlanOp.combine(pre, other.pre),
      graph = graph.mergeWith(other.graph),
      inputs = inputs ++ other.inputs,
      outputs = outputs ++ other.outputs
    )
  }
}
