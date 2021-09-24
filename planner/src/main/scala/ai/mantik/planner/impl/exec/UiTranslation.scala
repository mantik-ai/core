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

import ai.mantik.componently.collections.{DiGraph, DiLink}
import ai.mantik.elements.{MantikId, NamedMantikId}
import ai.mantik.planner.graph.Link
import ai.mantik.planner.{Plan, PlanFile, PlanFileReference, PlanNodeService, PlanOp}
import ai.mantik.ui.model.{
  Operation,
  OperationDefinition,
  OperationId,
  OperationState,
  RunGraph,
  RunGraphLink,
  RunGraphNode
}

/** Translates Plan Execution primitives into UI Primitives */
object UiTranslation {

  /** Translate a plan into a list of operations */
  def translateOperations(plan: Plan[_]): Seq[Operation] = {
    def make(subId: Either[String, List[Int]], definition: OperationDefinition): Operation = {
      val operationId = OperationId(subId)
      val operation = Operation(operationId, state = OperationState.Pending, definition = definition)
      operation
    }

    val alwaysPresent = Seq(
      make(Left(UiStateService.PrepareContainerName), OperationDefinition.PrepareContainers()),
      make(Left(UiStateService.PrepareFilesName), OperationDefinition.PrepareFiles(plan.files.size))
    )

    val planOperations = plan.flatWithCoordinates.map { case (coordinates, op) =>
      val translated = translateOp(plan, op)
      make(Right(coordinates), translated)
    }

    alwaysPresent ++ planOperations
  }

  /** Translate a PlanOp into an OperationDefinition. */
  private def translateOp(plan: Plan[_], planOp: PlanOp[_]): OperationDefinition = {
    def nameOf(mantikId: MantikId): Option[NamedMantikId] = {
      mantikId match {
        case n: NamedMantikId => Some(n)
        case _                => None
      }
    }
    planOp match {
      case r: PlanOp.RunGraph => OperationDefinition.RunGraph()
      case u: PlanOp.UploadFile =>
        OperationDefinition.UploadFile(
          fileRef = u.fileReference.id.toString,
          contentType = plan.fileMap.get(u.fileReference).map(_.contentType).getOrElse("Unknown"),
          contentSize = Some(u.data.length)
        )
      case a: PlanOp.AddMantikItem => OperationDefinition.ItemOp("add", a.item.itemId, nameOf(a.item.mantikId))
      case t: PlanOp.TagMantikItem =>
        OperationDefinition.ItemOp(
          "tag",
          t.item.itemId,
          Some(t.id)
        )
      case p: PlanOp.PushMantikItem =>
        OperationDefinition.ItemOp(
          "push",
          p.item.itemId,
          nameOf(p.item.mantikId)
        )
      case d: PlanOp.DeployAlgorithm =>
        OperationDefinition.UpdateDeployState(
          d.item.itemId,
          deploy = true
        )
      case d: PlanOp.DeployPipeline =>
        OperationDefinition.UpdateDeployState(
          d.item.itemId,
          deploy = true
        )
      case other => OperationDefinition.Other(other.getClass.getSimpleName)
    }
  }

  /** Translate the RunGraph-Operations into UI Model */
  def translateRunGraphs(plan: Plan[_]): Seq[(OperationId, RunGraph)] = {
    plan.flatWithCoordinates.collect { case ((coordinates, planOp: PlanOp.RunGraph)) =>
      OperationId(Right(coordinates)) -> translateRunGraph(plan, planOp)
    }
  }

  private def translateRunGraph(plan: Plan[_], runGraph: PlanOp.RunGraph): RunGraph = {
    // Note: the planOp graph has a slightly different understanding of links, as it also involves port references
    val nodes = runGraph.graph.nodes.view.mapValues(n => translateNode(plan, n.service)).toMap
    val links = runGraph.graph.links.map { link =>
      translateLink(runGraph, link)
    }.toVector
    DiGraph(nodes, links)
  }

  private def translateNode(plan: Plan[_], node: PlanNodeService): RunGraphNode = {
    node match {
      case f: PlanNodeService.File =>
        RunGraphNode.FileNode(
          f.fileReference.id,
          contentType = plan.fileMap.get(f.fileReference.id).map(_.contentType).getOrElse("Unknown")
        )
      case d: PlanNodeService.DockerContainer =>
        val payloadFileContentType = d.data
          .flatMap(id => plan.fileMap.get(id))
          .map(_.contentType)
          .orElse(d.embeddedData.map(_._1))

        RunGraphNode.MnpNode(
          d.container.image,
          parameters = d.container.parameters,
          mantikHeader = d.mantikHeader.toJsonValue,
          payloadFileContentType = payloadFileContentType,
          embeddedPayloadSize = d.embeddedData.map(_._2.length)
        )
    }
  }

  private def translateLink(graph: PlanOp.RunGraph, link: Link): DiLink[String, RunGraphLink] = {
    val contentType = graph.graph
      .resolveOutput(link.from)
      .map(_._2.contentType)
      .orElse(graph.graph.resolveInput(link.from).map(_._2.contentType))
      .getOrElse("Unknown")

    DiLink(
      from = link.from.node,
      to = link.to.node,
      data = RunGraphLink(
        fromPort = link.from.port,
        toPort = link.to.port,
        contentType = contentType,
        bytes = None
      )
    )
  }
}
