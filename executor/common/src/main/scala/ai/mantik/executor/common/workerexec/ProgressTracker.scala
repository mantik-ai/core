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
import ai.mantik.mnp.MnpSessionUrl
import ai.mantik.mnp.protocol.mnp.{QueryTaskResponse, TaskPortStatus}

import scala.concurrent.Future
import scala.concurrent.duration._
import cats.implicits._
import org.apache.commons.io.FileUtils

import scala.util.{Failure, Success}

/** Helper which tracks and prints progress of tasks within the graph.
  * TODO: Update the StateTracker with the information found.
  */
class ProgressTracker(sessions: RunningSessions, taskId: String)(implicit akkaRuntime: AkkaRuntime)
    extends ComponentBase {

  private val cancellable = actorSystem.scheduler.scheduleWithFixedDelay(0.second, 5.second) { () => printProgress() }

  private def printProgress(): Unit = {
    val progresses: Future[Vector[(MnpSessionUrl, QueryTaskResponse)]] = sessions.sessions.map { session =>
      session.mnpSession.task(taskId).query(false).map { queryResponse =>
        (session.mnpSessionUrl, queryResponse)
      }
    }.sequence
    progresses.andThen {
      case Failure(err) =>
        logger.warn(s"Could not fetch task status", err)
      case Success(value) =>
        val sorted = value.sortBy(_._1.toString)
        logger.debug(s"Periodic Task Status, size=${sorted.size}")
        sorted.zipWithIndex.foreach { case ((mnpUrl, queryResponse), idx) =>
          val error = if (queryResponse.error.nonEmpty) {
            s"Error: ${queryResponse.error}"
          } else ""
          logger.debug(
            s"${idx + 1}/${sorted.size} ${mnpUrl} ${error} ${queryResponse.state.toString()} " +
              s"input:${formatPortList(queryResponse.inputs)} outputs: ${formatPortList(queryResponse.outputs)}"
          )
        }
    }
  }

  private def formatPortList(list: Seq[TaskPortStatus]): String = {
    def formatSingle(s: TaskPortStatus): String = {
      val error = if (s.error.nonEmpty) {
        s"/Error: ${s.error}"
      } else ""
      val closed = if (s.done) {
        "/Done"
      } else ""
      s"${FileUtils.byteCountToDisplaySize(s.data)}/${s.msgCount}C${closed}${error}"
    }
    list.map(formatSingle).mkString("[", ",", "]")
  }

  def stop(): Unit = {
    cancellable.cancel()
  }

  addShutdownHook {
    stop()
    Future.successful(())
  }
}
