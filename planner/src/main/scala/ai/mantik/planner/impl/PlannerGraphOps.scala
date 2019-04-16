package ai.mantik.planner.impl

import ai.mantik.executor.model.{ Graph, Link, Node, NodeResource, NodeResourceRef }
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
    val missingRefs = for {
      Link(in, out) <- extraLinks
      ref <- Seq(in, out)
      if graph.resolveReference(ref).isEmpty
    } yield ref

    if (missingRefs.nonEmpty) {
      throw new Planner.InconsistencyException(s"Missing node references $missingRefs")
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
