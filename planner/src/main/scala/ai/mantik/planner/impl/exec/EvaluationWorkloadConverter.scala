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

import ai.mantik.componently.AkkaRuntime
import ai.mantik.executor.model.{
  DataSink,
  DataSource,
  EvaluationWorkload,
  EvaluationWorkloadBuilder,
  WorkloadContainer,
  WorkloadSession,
  WorkloadSessionPort,
  Link => ExecutorLink
}
import ai.mantik.planner.PlanExecutor.PlanExecutorException
import ai.mantik.planner.PlanNodeService
import ai.mantik.planner.PlanNodeService.DockerContainer
import ai.mantik.planner.graph.{Graph, Node, NodePortRef}
import ai.mantik.planner.repository.FileRepository
import ai.mantik.planner.repository.FileRepository.LoadFileResult
import akka.stream.scaladsl.Source
import akka.util.ByteString
import cats.data.State
import cats.implicits._

import scala.concurrent.Future

/**
  * Builds [[EvaluationWorkload]] from [[Graph]] of [[PlanNodeService]]
  *
  * Implementation Note: We are mixing Futures and State-Monads here, in future it would be better
  * to do all Future-Based preparations in advance.
  *
  * @param evaluationId user given evaluation id, must be distinct for each evaluation
  * @param graph graph to convert
  * @param openFiles prepared list of files to use.
  */
class EvaluationWorkloadConverter(
    evaluationId: String,
    graph: Graph[PlanNodeService],
    openFiles: ExecutionOpenFiles,
    fileRepository: FileRepository
)(implicit akkaRuntime: AkkaRuntime) {
  import ai.mantik.componently.AkkaHelper._

  type Builder = EvaluationWorkloadBuilder[String]

  /** Extracted docker nodes from graph. */
  private val dockerNodes: Vector[(String, Node[DockerContainer])] = graph.nodes.collect {
    case (id, c @ Node(d: DockerContainer, _, _)) =>
      id -> c.copy(service = d)
  }.toVector

  /** Temporary name for payload sources. */
  private def payloadSourceName(nodeId: String): String = s"payload:${nodeId}"

  /** Temporary name for sessions. */
  def sessionIdName(nodeId: String): String = s"session:${nodeId}"

  /** Temporary name for a link source. */
  private def linkSourceName(nodeId: String): String = s"linkSource:${nodeId}"

  /** Temporary name for a link target */
  private def linkTargetName(nodeId: String): String = s"linkTarget:${nodeId}"

  /** The resulting workload builder. */
  lazy val resultBuilder: Future[Builder] = {
    for {
      payloadSourcesStateChange <- prepareFileBasedPayloadInputs()
      linkStateChange <- prepareLinks()
    } yield {

      val combined = for {
        _ <- payloadSourcesStateChange
        _ <- prepareConstantBasedPayloadInputs
        _ <- prepareSessions
        _ <- linkStateChange
      } yield {
        ()
      }

      val initialBuilder: Builder = EvaluationWorkloadBuilder[String](evaluationId)
      combined.run(initialBuilder).value._1
    }
  }

  /** The resulting workload. */
  lazy val result: Future[EvaluationWorkload] = {
    resultBuilder.map(_.result)
  }

  /** Prepare DataSources which are responsible for file based payloads */
  private def prepareFileBasedPayloadInputs(): Future[State[Builder, Unit]] = {
    val loadFileResultsFuture: Future[Vector[(String, LoadFileResult)]] = (for {
      (nodeId, node) <- dockerNodes
      payloadFileReference <- node.service.data
      payloadEntry = openFiles.resolveFileRead(payloadFileReference)
    } yield {
      fileRepository.loadFile(payloadEntry.fileId).map { loadFileResult =>
        nodeId -> loadFileResult
      }
    }).sequence

    loadFileResultsFuture.map { loadFileResults =>
      State.modify[Builder] { builder =>
        loadFileResults.foldLeft(builder) { case (builder, (nodeId, loadFileResult)) =>
          val name = payloadSourceName(nodeId)
          val dataSource = DataSource(loadFileResult.contentType, loadFileResult.fileSize, loadFileResult.source)
          builder.addDataSource(name, dataSource)
        }
      }
    }
  }

  /** Prepare Data sources which are responsible for constant based payload inputs */
  private val prepareConstantBasedPayloadInputs: State[Builder, Unit] = {
    State.modify[Builder] { builder =>
      // ID, ContentType, Payload
      val idsAndPayload: Vector[(String, String, ByteString)] = (dockerNodes.collect {
        case (nodeId, node) if node.service.embeddedData.isDefined =>
          val embedded = node.service.embeddedData.get
          (nodeId, embedded._1, embedded._2)
      })
      idsAndPayload.foldLeft(builder) { case (builder, (nodeId, contentType, data)) =>
        val name = payloadSourceName(nodeId)
        // TODO: Configurable split size
        val pieces = data.grouped(65536).toVector
        val dataSource = DataSource(contentType, data.size, Source(pieces))
        builder.addDataSource(name, dataSource)
      }
    }
  }

  /** Prepare containers and sessions, assuming that Payloads are present */
  private val prepareSessions: State[Builder, Unit] = State.modify { builder =>
    dockerNodes.foldLeft(builder) { case (builder, (nodeId, node)) =>
      val workloadContainer: WorkloadContainer = WorkloadContainer(
        nameHint = Some("mantik-" + node.service.container.simpleImageName),
        container = node.service.container
      )

      val payloadId = for {
        payload <- builder.getDataSource(payloadSourceName(nodeId))
      } yield payload

      val (builderWithContainer, containerId) = builder.ensureContainer(workloadContainer.container, workloadContainer)

      /* We use the sessionId for accessing the session, but also as MnpSessionId. */
      val sessionId = sessionIdName(nodeId)

      val session = WorkloadSession(
        containerId = containerId,
        mnpSessionId = sessionId,
        mantikHeader = node.service.mantikHeader.toJsonValue,
        payload = payloadId,
        inputContentTypes = node.inputs.map(_.contentType),
        outputContentTypes = node.outputs.map(_.contentType)
      )

      builderWithContainer.addSession(sessionId, session)
    }
  }

  private def prepareLinks(): Future[State[Builder, Unit]] = {
    val stateChangesFuture: Future[Vector[State[Builder, ExecutorLink]]] = (graph.links.toVector.map { link =>
      for {
        linkSourceStateChange <- ensureLinkSource(link.from)
        linkTargetStateChange <- ensureLinkTarget(link.to)
      } yield {
        linkSourceStateChange.flatMap { linkSource =>
          linkTargetStateChange.map { linkTarget =>
            ExecutorLink(linkSource, linkTarget)
          }
        }
      }
    }).sequence

    stateChangesFuture.map { stateChanges =>
      State.modify[Builder] { builder =>
        stateChanges.foldLeft(builder) { case (builder, stateChange) =>
          val (nextState, link) = stateChange.run(builder).value
          val withLink = nextState.addLink(link)
          withLink
        }
      }
    }
  }

  private def ensureLinkSource(
      nodePortRef: NodePortRef
  ): Future[State[Builder, Either[Int, WorkloadSessionPort]]] = {
    graph.resolveOutput(nodePortRef) match {
      case None =>
        Future.failed(new PlanExecutorException(s"Missing link source: ${nodePortRef}"))
      case Some((node, _)) =>
        node.service match {
          case file: PlanNodeService.File =>
            require(nodePortRef.port == 0, "Links from files must start at port 0")
            val resolvedFile = openFiles.resolveFileRead(file.fileReference)
            fileRepository.loadFile(resolvedFile.fileId).map { loadFileResult =>
              val dataSource = DataSource(loadFileResult.contentType, loadFileResult.fileSize, loadFileResult.source)
              val name = linkSourceName(nodePortRef.node)
              State { builder =>
                val (updatedBuilder, id) = builder.ensureDataSource(name, dataSource)
                updatedBuilder -> Left(id)
              }
            }
          case _: PlanNodeService.DockerContainer =>
            Future {
              State { builder =>
                val sessionId = builder.sessions.get(sessionIdName(nodePortRef.node)).getOrElse {
                  throw new PlanExecutorException(s"Missing node ${nodePortRef.node}")
                }
                builder -> Right(WorkloadSessionPort(sessionId, nodePortRef.port))
              }
            }
        }
    }
  }

  private def ensureLinkTarget(
      nodePortRef: NodePortRef
  ): Future[State[Builder, Either[Int, WorkloadSessionPort]]] = {
    graph.resolveInput(nodePortRef) match {
      case None => Future.failed(new PlanExecutorException(s"Missing link target: ${nodePortRef}"))
      case Some((node, _)) =>
        node.service match {
          case file: PlanNodeService.File =>
            require(nodePortRef.port == 0, "Links from files must start at port 0")
            val resolvedFile = openFiles.resolveFileWrite(file.fileReference)
            fileRepository.storeFile(resolvedFile.fileId).map { storeFileResult =>
              val dataSink = DataSink(storeFileResult.sink)
              val name = linkTargetName(nodePortRef.node)
              State { builder =>
                val (updatedBuilder, id) = builder.ensureDataSink(name, dataSink)
                updatedBuilder -> Left(id)
              }
            }
          case _: PlanNodeService.DockerContainer =>
            Future.successful {
              State { builder =>
                val sessionId = builder.sessions.get(sessionIdName(nodePortRef.node)).getOrElse {
                  throw new PlanExecutorException(s"Missing node ${nodePortRef.node}")
                }
                builder -> Right(WorkloadSessionPort(sessionId, nodePortRef.port))
              }
            }

        }
    }
  }
}
