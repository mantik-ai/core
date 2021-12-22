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

import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.executor.model.{DataSink, DataSource, EvaluationWorkload, Link, WorkloadStatus}
import ai.mantik.mnp.{MnpSession, MnpSessionPortUrl}
import org.apache.commons.io.FileUtils

import scala.concurrent.Future

/**
  * Responsible for running links between containers and to run the evaluation itself
  * after everything has been prepared.
  *
  * Note: right now we can only evaluate graphs, where all inputs and outputs are connected to other nodes.
  * Missing links can lead to starving sessions.
  */
class LinkRunner(
    state: StateTracker,
    workload: EvaluationWorkload,
    sessions: RunningSessions,
    metrics: WorkerMetrics
)(implicit akkaRuntime: AkkaRuntime)
    extends ComponentBase {

  /** As we use the session only once, we can use a constant task Identifier. */
  val taskId = "evaluation"

  /** Run all the links within the workload. */
  def runLinks(): Future[Unit] = {
    val inputPushes = calculateInputPushes()
    val outputPulls = calculateOutputPulls()
    val queryTasks = calculateQueryTasks()

    val runningInputPushes = inputPushes.map(runInputPush)
    val runningOutputPulls = outputPulls.map(runOutputPull)
    val runQueries = queryTasks.map(runQuery)

    setAllLinksState(WorkloadStatus.Running)

    val progressTracker = new ProgressTracker(sessions, taskId)

    Future
      .sequence(
        runningInputPushes ++ runningOutputPulls ++ runQueries
      )
      .map { _ =>
        setAllLinksState(WorkloadStatus.Done)
        ()
      }
      .andThen { case _ =>
        progressTracker.stop()
      }
  }

  private def setAllLinksState(workloadState: WorkloadStatus): Unit = {
    state.updateEvaluationState(workload.id) { ev =>
      ev.copy(
        links = ev.links.map { links =>
          links.map(_.copy(status = workloadState))
        }
      )
    }
  }

  /** Data flowing from input into MNP Nodes */
  case class InputPush(
      source: DataSource,
      session: MnpSession,
      url: MnpSessionPortUrl,
      port: Int,
      linkId: Int
  )

  /** Data flowing from MNP Node into some output */
  case class OutputPull(
      sink: DataSink,
      session: MnpSession,
      url: MnpSessionPortUrl,
      port: Int,
      linkId: Int
  )

  /** The sessions which we have to bring to live explicitely as they only have output nodes */
  case class QueryTask(
      session: MnpSession
  )

  private def calculateInputPushes(): Vector[InputPush] = {
    workload.links.zipWithIndex.collect { case (Link(Left(dataSourceId), Right(workloadSessionPort)), linkId) =>
      val session = sessions.sessions(workloadSessionPort.sessionId)
      InputPush(
        workload.sources(dataSourceId),
        session.mnpSession,
        url = session.mnpSessionUrl.withPort(workloadSessionPort.port),
        port = workloadSessionPort.port,
        linkId = linkId
      )
    }
  }

  private def calculateOutputPulls(): Vector[OutputPull] = {
    workload.links.zipWithIndex.collect { case (Link(Right(workloadSessionPort), Left(dataSinkId)), linkId) =>
      val session = sessions.sessions(workloadSessionPort.sessionId)
      OutputPull(
        workload.sinks(dataSinkId),
        session = sessions.sessions(workloadSessionPort.sessionId).mnpSession,
        url = session.mnpSessionUrl.withPort(workloadSessionPort.port),
        port = workloadSessionPort.port,
        linkId = linkId
      )
    }
  }

  private def calculateQueryTasks(): Vector[QueryTask] = {
    // We look for sessions without inputs
    workload.sessions.zipWithIndex.collect {
      case (session, sessionId) if session.inputContentTypes.isEmpty =>
        QueryTask(sessions.sessions(sessionId).mnpSession)
    }
  }

  private def runInputPush(inputPush: InputPush): Future[Unit] = {
    val description = s"${FileUtils.byteCountToDisplaySize(inputPush.source.byteCount)} to ${inputPush.url}"
    val t0 = System.currentTimeMillis()
    logger.debug(s"Running Input Push: $description")
    val task = inputPush.session.task(taskId)
    val sink = task.push(inputPush.port)

    inputPush.source.source
      .map { bytes =>
        logger.trace(s"Pushing ${description}: ${FileUtils.byteCountToDisplaySize(bytes.length)}")
        metrics.mnpPushBytes.inc(bytes.length)
        state.updateLinkState(workload.id, inputPush.linkId)(_.incrementTransferred(bytes.length))
        bytes
      }
      .runWith(sink)
      .map { case (bytes, _) =>
        val t1 = System.currentTimeMillis()
        state.updateLinkState(workload.id, inputPush.linkId)(_.copy(status = WorkloadStatus.Done))
        logger.debug(
          s"Finished input push ${description} in ${t1 - t0}ms"
        )
      }
  }

  private def runOutputPull(outputPull: OutputPull): Future[Unit] = {
    val t0 = System.currentTimeMillis()
    val task = outputPull.session.task(taskId)
    val source = task.pull(outputPull.port)
    logger.debug(s"Running Output pull: ${outputPull.url}")
    source
      .map { bytes =>
        logger.trace(s"Pulling ${outputPull.url}: ${FileUtils.byteCountToDisplaySize(bytes.length)}")
        metrics.mnpPullBytes.inc(bytes.length)
        state.updateLinkState(workload.id, outputPull.linkId)(_.incrementTransferred(bytes.length))
        bytes
      }
      .runWith(outputPull.sink.sink)
      .map { bytes =>
        state.updateLinkState(workload.id, outputPull.linkId)(_.copy(status = WorkloadStatus.Done))
        val t1 = System.currentTimeMillis()
        logger.debug(
          s"Finished output pull ${FileUtils.byteCountToDisplaySize(bytes)} from ${outputPull.url} in ${t1 - t0}ms"
        )
      }
  }

  private def runQuery(queryTask: QueryTask): Future[Unit] = {
    val sessionUrl = queryTask.session.mnpUrl
    val t0 = System.currentTimeMillis()
    logger.debug(s"Querying task ${taskId} in $sessionUrl to start")
    queryTask.session.task(taskId).query(true).map { response =>
      val t1 = System.currentTimeMillis()
      logger.debug(s"Queried task $sessionUrl in ${t1 - t0}ms, state=${response.state}")
      ()
    }
  }
}
