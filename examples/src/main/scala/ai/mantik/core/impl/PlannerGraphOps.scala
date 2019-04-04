package ai.mantik.core.impl

import ai.mantik.core
import ai.mantik.core.Planner
import ai.mantik.executor.model.{ Graph, Link, Node, NodeResourceRef }

/** Extends the graph with some convenience methods. */
private[impl] class PlannerGraphOps(graph: Graph) {

  /** Add nodes to the graph, returns a lefty error if nodes are already existant. */
  def addNodes(extraNodes: Map[String, Node]): Graph = {
    val existant = extraNodes.keySet.filter(graph.nodes.contains)
    if (existant.nonEmpty) {
      throw new Planner.InconsistencyException(s"Double add nodes ${existant}")
    } else {
      graph.copy(nodes = graph.nodes ++ extraNodes)
    }
  }

  /** Add links to the graph. */
  def addLinks(extraLinks: Link*): Graph = {
    val missingNodes = for {
      Link(in, out) <- extraLinks
      nodeName <- Seq(in, out)
      if !graph.nodes.get(nodeName.node).exists(_.resources.contains(nodeName.resource))
    } yield nodeName

    if (missingNodes.nonEmpty) {
      throw new core.Planner.InconsistencyException(s"Missing node references ${missingNodes}")
    }
    graph.copy(
      links = graph.links ++ extraLinks
    )
  }

  def mergeWith(other: Graph): Graph = {
    import PlannerGraphOps._
    addNodes(other.nodes).addLinks(other.links: _*)
  }
}

private[impl] object PlannerGraphOps {
  import scala.language.implicitConversions
  implicit def toGraphOps(graph: Graph): PlannerGraphOps = new PlannerGraphOps(graph)
}
