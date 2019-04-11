package ai.mantik.planner.impl

import ai.mantik.executor.model.{ Graph, Link, Node }
import ai.mantik.planner.Planner

/** Extends the graph with some convenience methods. */
private[impl] class PlannerGraphOps[T](graph: Graph[T]) {

  /** Add nodes to the graph, returns a lefty error if nodes are already existent. */
  def addNodes(extraNodes: Map[String, Node[T]]): Graph[T] = {
    val existent = extraNodes.keySet.filter(graph.nodes.contains)
    if (existent.nonEmpty) {
      throw new Planner.InconsistencyException(s"Double add nodes $existent")
    } else {
      graph.copy(nodes = graph.nodes ++ extraNodes)
    }
  }

  /** Add links to the graph. */
  def addLinks(extraLinks: Link*): Graph[T] = {
    val missingNodes = for {
      Link(in, out) <- extraLinks
      nodeName <- Seq(in, out)
      if !graph.nodes.get(nodeName.node).exists(_.resources.contains(nodeName.resource))
    } yield nodeName

    if (missingNodes.nonEmpty) {
      throw new Planner.InconsistencyException(s"Missing node references $missingNodes")
    }
    graph.copy(
      links = graph.links ++ extraLinks
    )
  }

  def mergeWith(other: Graph[T]): Graph[T] = {
    import PlannerGraphOps._
    addNodes(other.nodes).addLinks(other.links: _*)
  }
}

private[impl] object PlannerGraphOps {
  import scala.language.implicitConversions
  implicit def toGraphOps[T](graph: Graph[T]): PlannerGraphOps[T] = new PlannerGraphOps(graph)
}
