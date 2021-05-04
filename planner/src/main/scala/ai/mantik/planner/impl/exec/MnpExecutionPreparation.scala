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

import ai.mantik.bridge.protocol.bridge.MantikInitConfiguration
import ai.mantik.mnp.protocol.mnp.{ConfigureInputPort, ConfigureOutputPort}
import ai.mantik.planner.PlanNodeService.DockerContainer
import ai.mantik.planner.Planner.InconsistencyException
import ai.mantik.planner.impl.exec.MnpExecutionPreparation._
import ai.mantik.planner.repository.ContentTypes
import ai.mantik.planner.repository.FileRepository.{FileGetResult, FileStorageResult}
import ai.mantik.planner._
import ai.mantik.planner.graph.{Graph, Node, NodePortRef}
import akka.http.scaladsl.model.Uri

/** Information for execution of a [[ai.mantik.planner.PlanOp.RunGraph]] */
case class MnpExecutionPreparation(
    sessionInitializers: Map[String, SessionInitializer],
    inputPushs: Vector[InputPush],
    outputPulls: Vector[OutputPull],
    taskQueries: Vector[TaskQuery]
)

object MnpExecutionPreparation {

  /** A prepared session initializer */
  case class SessionInitializer(
      sessionId: String,
      config: MantikInitConfiguration,
      inputPorts: Vector[ConfigureInputPort],
      outputPorts: Vector[ConfigureOutputPort]
  )

  /** A Prepared input push */
  case class InputPush(
      nodeId: String,
      portId: Int,
      fileGetResult: FileGetResult
  )

  /** A prepared output pull */
  case class OutputPull(
      nodeId: String,
      portId: Int,
      fileStorageResult: FileStorageResult
  )

  /** A prepared task query. */
  case class TaskQuery(
      nodeId: String
  )
}

/** Builds [[MnpExecutionPreparation]] */
class MnpExecutionPreparer(
    graphId: String,
    graph: Graph[PlanNodeService],
    containerAddresses: Map[String, String], // maps nodeId to running name + port
    files: ExecutionOpenFiles,
    remoteFileUrls: Map[String, String] // maps nodeId to URLs of file payload
) {

  def build(): MnpExecutionPreparation = {
    val initializers = graph.nodes.collect { case (nodeId, Node(d: DockerContainer, _, _)) =>
      nodeId -> buildSessionCall(nodeId, d)
    }

    val inputPushes = collectInputPushes()
    val outputPulls = collectOutputPulls()
    val taskQueries = collectTaskQueries()
    MnpExecutionPreparation(
      initializers,
      inputPushes,
      outputPulls,
      taskQueries
    )
  }

  private def buildSessionCall(nodeId: String, dockerContainer: DockerContainer): SessionInitializer = {
    val sessionId = sessionIdForNode(nodeId)

    val inputData = dockerContainer.data.map { data =>
      files.resolveFileRead(data.id)
    }

    val graphNode = graph.nodes(nodeId)

    val inputPorts = graphNode.inputs.map { input =>
      ConfigureInputPort(input.contentType)
    }

    val outputPorts = graphNode.outputs.zipWithIndex.map { case (output, outPort) =>
      val forwarding = graph.links.collect {
        case link
            if link.from.node == nodeId && link.from.port == outPort && graph
              .nodes(link.to.node)
              .service
              .isInstanceOf[DockerContainer] =>
          mnpUrlForResource(link.from, link.to)
      }
      val singleForwarding = forwarding match {
        case x if x.isEmpty => None
        case Seq(single)    => Some(single)
        case multiple =>
          throw new InconsistencyException(
            s"Only a single forwarding is allowed, broken plan? found ${multiple} as goal of ${nodeId}"
          )
      }
      ConfigureOutputPort(
        contentType = output.contentType,
        destinationUrl = singleForwarding.getOrElse("")
      )
    }

    val initConfiguration = MantikInitConfiguration(
      header = dockerContainer.mantikHeader.toJson,
      payloadContentType = inputData.map(_.contentType).getOrElse(""),
      payload = inputData
        .map { data =>
          val fullUrl = remoteFileUrls
            .getOrElse(nodeId, throw new InconsistencyException(s"No remote url found for node id ${nodeId}"))
          MantikInitConfiguration.Payload.Url(fullUrl)
        }
        .getOrElse(
          MantikInitConfiguration.Payload.Empty
        )
    )

    MnpExecutionPreparation.SessionInitializer(sessionId, initConfiguration, inputPorts, outputPorts)
  }

  private def collectInputPushes(): Vector[InputPush] = {
    graph.links.flatMap { link =>
      val to = graph.nodes(link.to.node)
      val from = graph.nodes(link.from.node)
      from.service match {
        case fromFile: PlanNodeService.File =>
          to.service match {
            case toFile: PlanNodeService.File =>
              throw new InconsistencyException("Links from file to file should not happen inside the graph")
            case toNode: PlanNodeService.DockerContainer =>
              Some(
                InputPush(
                  nodeId = link.to.node,
                  portId = link.to.port,
                  fileGetResult = files.resolveFileRead(fromFile.fileReference)
                )
              )
          }
        case _ => None
      }
    }.toVector
  }

  private def collectOutputPulls(): Vector[OutputPull] = {
    graph.links.flatMap { link =>
      val to = graph.nodes(link.to.node)
      val (from, fromResource) = graph.resolveOutput(link.from).getOrElse {
        throw new InconsistencyException(s"Could not resolve reference ${link.from}")
      }
      from.service match {
        case container: PlanNodeService.DockerContainer =>
          to.service match {
            case file: PlanNodeService.File =>
              Some(
                OutputPull(
                  nodeId = link.from.node,
                  portId = link.from.port,
                  fileStorageResult = files.resolveFileWrite(file.fileReference)
                )
              )
            case _ =>
              None
          }
        case _ =>
          None
      }
    }.toVector
  }

  private def collectTaskQueries(): Vector[TaskQuery] = {
    // Task Queries are necessary if a node has no inputs but outputs (DataSets) because it may be forwarded
    graph.nodes.collect {
      case (nodeId, Node(_: PlanNodeService.DockerContainer, _, _)) if !graph.links.exists(_.to.node == nodeId) =>
        TaskQuery(
          nodeId = nodeId
        )
    }.toVector
  }

  private def mnpUrlForResource(from: NodePortRef, target: NodePortRef): String = {
    val fromAddress = containerAddresses.getOrElse(
      from.node,
      throw new InconsistencyException(s"Container ${from.node} not prepared?")
    )
    val port = target.port
    val sessionId = sessionIdForNode(target.node)
    val address = containerAddresses.getOrElse(
      target.node,
      throw new InconsistencyException(s"Container ${target.node} not prepared?")
    )

    val addressToUse = if (fromAddress == address) {
      /*
      Special case: A connection back to the container
      In Tests kubernetes sometimes has problems to resolve container pods own address
       */
      val port = address.indexOf(':') match {
        case -1 => 8502
        case n  => address.substring(n + 1).toInt
      }
      s"localhost:$port"
    } else {
      address
    }

    s"mnp://$addressToUse/$sessionId/$port"
  }

  private def sessionIdForNode(nodeId: String): String = {
    graphId + "_" + nodeId
  }

}
