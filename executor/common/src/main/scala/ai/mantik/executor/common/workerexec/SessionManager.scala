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
import ai.mantik.executor.{Errors, PayloadProvider}
import ai.mantik.executor.model.{
  DataSource,
  EvaluationWorkload,
  Link,
  WorkloadSession,
  WorkloadSessionPort,
  WorkloadStatus
}
import ai.mantik.mnp.{MnpSession, MnpSessionPortUrl}
import ai.mantik.mnp.protocol.mnp.{ConfigureInputPort, ConfigureOutputPort}
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import cats.implicits._
import org.apache.commons.io.FileUtils

import scala.concurrent.Future
import scala.util.{Failure, Success}

/** Handles the lifecycle of Sessions inside evaluations. */
class SessionManager(state: StateTracker, payloadProvider: PayloadProvider)(implicit akkaRuntime: AkkaRuntime)
    extends ComponentBase {

  /** Threshold, after which payload is provided via PayloadProvider instead of constant value */
  val payloadUploadThreshold: Int = config.getInt("mantik.executor.payloadUploadThreshold")

  /** Initialize Sessions within Containers. */
  def initializeSessions(
      workload: EvaluationWorkload,
      containers: RunningContainers
  ): Future[RunningSessions] = {

    val futures = workload.sessions.zipWithIndex.map { case (session, sessionId) =>
      val container = containers.containers.get(session.containerId).getOrElse {
        throw new Errors.InternalException(s"No container found for referenced container ${session.containerId}")
      }
      initializeSession(containers, workload, container, sessionId, session).map { mnpSession =>
        RunningSession(mnpSession, container.mnpAddress.withSession(session.mnpSessionId))
      }
    }
    futures.sequence.map { sessions =>
      RunningSessions(sessions)
    }
  }

  private def initializeSession(
      runningContainers: RunningContainers,
      workload: EvaluationWorkload,
      container: ReservedContainer,
      sessionId: Int,
      session: WorkloadSession
  ): Future[MnpSession] = {
    val sessionUrl = container.mnpAddress.withSession(session.mnpSessionId)
    val t0 = System.currentTimeMillis()
    logger.debug(s"Initializing session $sessionUrl, ${session.mantikHeader}")

    state.updateSessionState(workload.id, sessionId)(
      _.copy(
        status = WorkloadStatus.Preparing(Some("Preparing Payload"))
      )
    )

    val maybePayloadFuture: Future[Option[PreparedPayload]] = session.payload.map { payloadId =>
      preparePayload(workload, payloadId, true)
    }.sequence

    maybePayloadFuture.flatMap { maybePayload =>
      state.updateSessionState(workload.id, sessionId)(
        _.copy(
          status = WorkloadStatus.Preparing(Some("Starting session"))
        )
      )
      val payloadField = maybePayload match {
        case None => MantikInitConfiguration.Payload.Empty
        case Some(PreparedPayload(_, Left(constant))) =>
          MantikInitConfiguration.Payload.Content(
            RpcConversions.encodeByteString(constant)
          )
        case Some(PreparedPayload(_, Right(url))) =>
          MantikInitConfiguration.Payload.Url(
            url
          )
      }

      val mantikInitConfiguration = MantikInitConfiguration(
        header = session.mantikHeader.toString(),
        payloadContentType = maybePayload.map(_.source.contentType).getOrElse(""),
        payload = payloadField
      )

      val outputs = calculateOutputsFor(runningContainers, workload, sessionId)
      logger.debug(s"Outputs for ${sessionUrl}: ${outputs}")

      container.mnpClient
        .initSession(
          session.mnpSessionId,
          config = Some(mantikInitConfiguration),
          inputs = session.inputContentTypes.map(ct => ConfigureInputPort(ct)),
          outputs = outputs
        )
        .andThen {
          case Success(_) =>
            val t1 = System.currentTimeMillis()
            logger.debug(s"Initialized session $sessionUrl within ${t1 - t0}ms")
            state.updateSessionState(workload.id, sessionId)(
              _.copy(
                status = WorkloadStatus.Running
              )
            )
          case Failure(exception) =>
            state.updateSessionState(workload.id, sessionId)(
              _.copy(
                status =
                  WorkloadStatus.Failed(s"Could not init session: ${Option(exception.getMessage).getOrElse("unknown")}")
              )
            )
        }
    }
  }

  /** Prepared upload for execution
    * @param source the data source
    * @param content the content, either uploaded
    */
  case class PreparedPayload(
      source: DataSource,
      content: Either[ByteString, String]
  )

  /** Prepare payload for a session.
    * @param workload the workload.
    * @param payloadId the workload's source id
    * @param temporary if true, the payload will be temporary
    */
  def preparePayload(workload: EvaluationWorkload, payloadId: Int, temporary: Boolean): Future[PreparedPayload] = {
    val source = workload.sources(payloadId)
    if (source.byteCount >= payloadUploadThreshold) {
      logger.debug(
        s"Preparing Payload ${payloadId} of size ${FileUtils.byteCountToDisplaySize(source.byteCount)} via Payload Provider"
      )
      payloadProvider.provide(workload.id, temporary, source.source, source.byteCount, source.contentType).map { url =>
        PreparedPayload(source, Right(url))
      }
    } else {
      logger.debug(
        s"Preparing Payload ${payloadId} of size ${FileUtils.byteCountToDisplaySize(source.byteCount)} as Constant"
      )
      source.source.runWith(Sink.seq).map { collected =>
        val concatenated = collected.foldLeft(ByteString.empty)(_ ++ _)
        PreparedPayload(source, Left(concatenated))
      }
    }
  }

  /** Calculate how output ports are forwarded. */
  private def calculateOutputsFor(
      runningContainers: RunningContainers,
      workload: EvaluationWorkload,
      sessionId: Int
  ): Vector[ConfigureOutputPort] = {
    workload.sessions(sessionId).outputContentTypes.zipWithIndex.map { case (contentType, portIndex) =>
      // Let's see if the data is forwarded to some place
      val destinationUrl = workload.links.collect {
        case Link(
              Right(WorkloadSessionPort(linkSessionId, linkSessionPort)),
              Right(target: WorkloadSessionPort)
            ) if linkSessionId == sessionId && linkSessionPort == portIndex =>
          mnpUrlForDestinationPort(
            runningContainers,
            workload,
            linkSessionId,
            linkSessionPort,
            target.sessionId,
            target.port
          )
      }.toList match {
        case Nil       => None
        case List(url) => Some(url)
        case multiples =>
          throw new Errors.BadRequestException(
            s"Plan contains multiple forwarding outputs from ${sessionId}/${portIndex} to ${multiples}"
          )
      }
      ConfigureOutputPort(contentType, destinationUrl = destinationUrl.map(_.toString).getOrElse(""))
    }
  }

  /** Figures out the MNP Url for a session port within the graph. */
  private def mnpUrlForDestinationPort(
      runningContainers: RunningContainers,
      workload: EvaluationWorkload,
      sourceSessionId: Int,
      sourcePortId: Int,
      targetSessionId: Int,
      targetPortId: Int
  ): MnpSessionPortUrl = {
    val sourceSession = workload.sessions(sourceSessionId)
    val targetSession = workload.sessions(targetSessionId)
    val targetContainer = runningContainers.containers(targetSession.containerId)
    val candidate = targetContainer.mnpAddress.withSession(targetSession.mnpSessionId).withPort(targetPortId)
    if (sourceSession.containerId == targetSession.containerId) {
      // Workaround, in some circumstances Kubernetes Pods seem not able to resolve themself using their node address
      /*
      require(
        candidate.address.startsWith(targetContainer.name),
        s"Unexpected target address: ${candidate.address}, expected it to start with ${targetContainer.name}"
      )
       */
      val updatedAddress = "localhost" + candidate.address.stripPrefix(targetContainer.name)
      val replacement = MnpSessionPortUrl.build(
        updatedAddress,
        candidate.session,
        candidate.port
      )
      logger.debug(
        s"Applying localhost forward URL workaround from ${candidate} to ${replacement} for node ${targetContainer.name}"
      )
      replacement
    } else {
      candidate
    }
  }
}
