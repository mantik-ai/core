package ai.mantik.planner.impl

import ai.mantik.executor.model.{ Graph, Link, NodeResourceRef }
import ai.mantik.planner.Plan
import PlannerGraphOps._

/**
 * Describes a way a resource is calculated (a containing part of a graph).
 *
 * @param preplan a plan which must be executed before evaluating the graph
 * @param graph graph to evaluate
 * @param inputs input resources
 * @param outputs output resources
 *
 * Node: resources may be input and output at the same time.
 */
private[impl] case class ResourcePlan(
    preplan: Plan = Plan.Empty,
    graph: Graph,
    inputs: Seq[NodeResourceRef] = Nil,
    outputs: Seq[NodeResourceRef] = Nil
) {

  def prependPlan(plan: Plan): ResourcePlan = {
    copy(
      preplan = Plan.combine(plan, preplan)
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
      preplan = Plan.combine(preplan, argument.preplan),
      graph = graph.mergeWith(argument.graph).addLinks(extraLinks: _*),
      inputs = inputs.drop(argument.outputs.size),
      outputs = outputs
    )
  }
}
