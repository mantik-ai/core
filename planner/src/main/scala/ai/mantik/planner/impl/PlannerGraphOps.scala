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

import ai.mantik.planner.Planner
import ai.mantik.planner.graph.{Graph, Link, Node}

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
    val missingOutputs = extraLinks.collect {
      case link if graph.resolveOutput(link.from).isEmpty => link.from
    }
    val missingInputs = extraLinks.collect {
      case link if graph.resolveInput(link.to).isEmpty => link.to
    }

    if (missingOutputs.nonEmpty || missingInputs.nonEmpty) {
      throw new Planner.InconsistencyException(s"Missing references outputs: $missingOutputs, inputs: $missingInputs")
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
