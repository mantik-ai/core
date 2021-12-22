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
package ai.mantik.executor.common.workerexec

import ai.mantik.bridge.protocol.bridge.MantikInitConfiguration
import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.componently.rpc.RpcConversions
import ai.mantik.componently.utils.FutureHelper
import ai.mantik.executor.common.workerexec.model.{
  MnpPipelineDefinition,
  MnpWorkerDefinition,
  StartWorkerRequest,
  StartWorkerResponse,
  StopWorkerRequest
}
import ai.mantik.executor.{Errors, PayloadProvider}
import ai.mantik.executor.model.{
  EvaluationDeploymentRequest,
  EvaluationDeploymentResponse,
  EvaluationWorkload,
  Link,
  WorkloadSession,
  WorkloadSessionPort
}
import ai.mantik.mnp.{MnpAddressUrl, MnpSessionUrl}
import ai.mantik.mnp.protocol.mnp.{ConfigureInputPort, ConfigureOutputPort, InitRequest}
import akka.util.ByteString
import com.google.protobuf.any.Any
import io.circe.syntax._

import scala.concurrent.Future
import cats.implicits._

import scala.util.{Failure, Success}

/** Responsible for handling deployments */
class DeploymentManager(
    backend: WorkerExecutorBackend,
    metrics: WorkerMetrics,
    sessionManager: SessionManager,
    payloadProvider: PayloadProvider
)(implicit akkaRuntime: AkkaRuntime)
    extends ComponentBase {

  /** Start a deployment. */
  def startDeployment(deploymentRequest: EvaluationDeploymentRequest): Future[EvaluationDeploymentResponse] = {
    if (deploymentRequest.violations.nonEmpty) {
      return Future.failed(new Errors.BadRequestException(s"Violations in request: ${deploymentRequest.violations}"))
    }

    val id = deploymentRequest.workload.id
    logger.info(s"Starting Deployment ${id} consisting of ${deploymentRequest.workload.sessions.size} sessions.")

    // One worker per Session, and the pipeline putting them together
    // (We cannot share Sessions on a node when using the initializer sidecar)
    val workerRequestsFuture: Future[Vector[StartWorkerRequest]] = deploymentRequest.workload.sessions.map { session =>
      val container = deploymentRequest.workload.containers(session.containerId)

      buildInitCallForDeployment(session, deploymentRequest.workload).map { initRequest =>
        val encodedInitializer = ByteString(initRequest.toByteArray)
        StartWorkerRequest(
          id = id,
          definition = MnpWorkerDefinition(
            container = container.container,
            initializer = Some(encodedInitializer)
          ),
          keepRunning = true,
          nameHint = container.nameHint,
          ingressName = None
        )
      }
    }.sequence

    val workerResponseFuture: Future[Vector[StartWorkerResponse]] = workerRequestsFuture.flatMap { requests =>
      requests.map { request =>
        backend.startWorker(request).map { response =>
          logger.debug(s"Started worker ${response.nodeName} for deployment ${id}")
          metrics.permanentWorkersCreated.inc()
          response
        }
      }.sequence
    }

    val pipelineResponse: Future[StartWorkerResponse] = workerResponseFuture.flatMap { workerResponses =>
      val pipelineRequest = buildPipelineRequest(deploymentRequest, workerResponses)
      backend.startWorker(pipelineRequest).map { response =>
        logger.debug(s"Started worker ${response.nodeName} as pipeline controller for deployment ${id}")
        metrics.permanentPipelinesCreated.inc()
        response
      }
    }

    val translatedResponse = pipelineResponse.map { pipelineResponse =>
      EvaluationDeploymentResponse(
        pipelineResponse.internalUrl,
        externalUrl = pipelineResponse.externalUrl
      )
    }

    FutureHelper.andThenAsync(translatedResponse) {
      case Success(_) =>
        logger.info(s"Deployment of ${id} succeeded")
        Future.successful(())
      case Failure(error) =>
        logger.error(s"Deployment of ${id} failed", error)
        // Removing stuff laying around
        removeDeployment(id)
    }
  }

  /** Build the embedded InitRequest for a Session which is about to be deployed. */
  private def buildInitCallForDeployment(
      session: WorkloadSession,
      workload: EvaluationWorkload
  ): Future[InitRequest] = {
    val inputPorts: Vector[ConfigureInputPort] = session.inputContentTypes.map { contentType =>
      ConfigureInputPort(contentType)
    }
    val outputPorts: Vector[ConfigureOutputPort] = session.outputContentTypes.map { contentType =>
      ConfigureOutputPort(contentType)
    }

    session.payload
      .map { payloadSourceId =>
        sessionManager.preparePayload(workload, payloadSourceId, temporary = false)
      }
      .sequence
      .map { maybePreparedPayload =>
        val initConfiguration = MantikInitConfiguration(
          header = session.mantikHeader.toString(),
          payloadContentType = maybePreparedPayload.map(_.source.contentType).getOrElse(""),
          payload = maybePreparedPayload match {
            case None => MantikInitConfiguration.Payload.Empty
            case Some(prepared) =>
              prepared.content match {
                case Left(constant) =>
                  MantikInitConfiguration.Payload.Content(RpcConversions.encodeByteString(constant))
                case Right(url) => MantikInitConfiguration.Payload.Url(url)
              }
          }
        )

        InitRequest(
          sessionId = session.mnpSessionId,
          configuration = Some(Any.pack(initConfiguration)),
          inputs = inputPorts,
          outputs = outputPorts
        )
      }
  }

  /** Build a request for the pipelien helper from a workload request. */
  private def buildPipelineRequest(
      request: EvaluationDeploymentRequest,
      sessions: Vector[StartWorkerResponse]
  ): StartWorkerRequest = {
    val mnpSessionUrls: Vector[MnpSessionUrl] =
      request.workload.sessions.zip(sessions).map { case (session, response) =>
        MnpAddressUrl
          .parse(response.internalUrl)
          .getOrElse {
            throw new Errors.InternalException(
              s"Expected an MNP Address URL from Backends response, got ${response.internalUrl}"
            )
          }
          .withSession(session.mnpSessionId)
      }
    val pipelineRuntimeDefinition = DeploymentManager.buildPipelineDefinition(request, mnpSessionUrls)

    StartWorkerRequest(
      id = request.workload.id,
      definition = MnpPipelineDefinition(
        pipelineRuntimeDefinition.asJson
      ),
      keepRunning = true,
      nameHint = request.nameHint,
      ingressName = request.ingressName
    )
  }

  /** Remove a deployment */
  def removeDeployment(id: String): Future[Unit] = {
    for {
      // TODO: we are using the ids as Tag id. This should be better reflected in code.
      _ <- backend.stopWorker(StopWorkerRequest(idFilter = Some(id)))
      _ <- payloadProvider.delete(id)
    } yield {
      ()
    }
  }
}

private[workerexec] object DeploymentManager {

  /** Build the runtime pipeline defintiion. */
  def buildPipelineDefinition(
      request: EvaluationDeploymentRequest,
      sessionUrls: Vector[MnpSessionUrl]
  ): PipelineRuntimeDefinition = {
    // TODO: the pipe line runner only supports linear pipelines.
    // In future we should also support non linear pielines, but this would need a patched Pipeline Manager Container
    // and support inside Mantik Planner

    if (request.input.port != 0 || request.output.port != 0) {
      throw new Errors.NotSupportedException(s"Only pipelines from port 0 to 0 supported right now")
    }

    /** Carries current evaluation state
      * @param visitedSessions session ids already visited (for loop detection)
      * @param lastSession the previous session id
      */
    case class State(
        visitedSessions: Set[Int],
        lastSession: Int
    )

    val initialState = State(
      Set.empty,
      request.input.sessionId
    )

    val nextSessionIds: Vector[Int] = Vector.unfold(initialState) {
      case State(_, lastSession) if lastSession == request.output.sessionId =>
        // terminate
        None
      case State(visited, lastSession) =>
        val nextSession = findNextSessionId(request, lastSession)
        if (visited.contains(nextSession)) {
          throw new Errors.BadRequestException(s"Loop in pipeline detected.")
        } else {
          Some(nextSession -> State(visited + nextSession, nextSession))
        }
    }

    val sessionIds = initialState.lastSession +: nextSessionIds
    val steps = sessionIds.map { id =>
      PipelineRuntimeDefinition.Step(sessionUrls(id).toString)
    }

    PipelineRuntimeDefinition(
      name = request.nameHint.getOrElse(request.workload.id),
      steps = steps,
      inputType = request.inputDataType,
      outputType = request.outputDataType
    )
  }

  /** Find the next session id
    * (Only linear pipelines using port 0 allowed)
    */
  private def findNextSessionId(request: EvaluationDeploymentRequest, currentSession: Int): Int = {
    val destination = request.workload.links
      .collectFirst {
        case Link(Right(WorkloadSessionPort(sessionId, inportId)), Right(next))
            if sessionId == currentSession && inportId == 0 =>
          next
      }
      .getOrElse {
        throw new Errors.BadRequestException(s"Could not figure out session after ${currentSession}")
      }
    if (destination.port != 0) {
      throw new Errors.NotSupportedException(s"Only pipelines from port 0 to 0 supported right now")
    }
    destination.sessionId
  }
}
