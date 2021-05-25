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
package ai.mantik.mnp

import ai.mantik.componently.{ AkkaRuntime, ComponentBase }
import ai.mantik.mnp.protocol.mnp.{ AboutResponse, InitRequest, SessionState }
import ai.mantik.mnp.server.{ ServerBackend, ServerSession, ServerTask }
import akka.{ Done, NotUsed }
import akka.stream.scaladsl.{ Flow, Sink, Source }
import akka.util.ByteString

import scala.concurrent.Future

class DummyBackend(implicit akkaRuntime: AkkaRuntime) extends ComponentBase with ServerBackend {
  override def about(): Future[AboutResponse] = Future.successful(
    AboutResponse("DummyBackend")
  )

  private var sessions: Map[String, Session] = Map.empty
  var quitted = false

  /** If set, crash upon init session */
  var sessionInitCrash: Option[RuntimeException] = None

  class Session(val sessionId: String, val inputs: Int, val outputs: Int) extends ServerSession {
    var sessionQuitted = false
    private var tasks: Map[String, Task] = Map.empty

    // If set, let created tasks crash
    var crashingTask: Option[RuntimeException] = None

    override def shutdown(): Future[Unit] = {
      sessionQuitted = true
      Future.successful(())
    }

    def getTask(taskId: String): Option[Task] = tasks.get(taskId)

    override def runTask(taskId: String): Future[ServerTask] = {
      val task = new Task(taskId, this, crashingTask)
      tasks += (taskId -> task)
      Future.successful(task)
    }
  }

  class Task(val taskId: String, session: Session, crash: Option[Exception]) extends ServerTask {
    require(session.inputs == session.outputs)
    var isShutdown = false

    private val loops = Vector.fill(session.inputs)(new Loop())

    override def push(id: Int, source: Source[ByteString, NotUsed]): Future[Done] = {
      loops(id).push(source)
    }

    override def pull(id: Int): Source[ByteString, NotUsed] = {
      loops(id).pull()
    }

    override def finished: Future[Done] = {
      crash match {
        case Some(exception) =>
          return Future.failed(exception)
        case None =>
      }
      Future.sequence(loops.map(_.done)).map { _ => Done }
    }

    override def shutdown(): Unit = {
      isShutdown = true
    }
  }

  def getSession(sessionId: String): Option[Session] = sessions.get(sessionId)

  override def init(initRequest: InitRequest, stateFn: (SessionState, Option[String]) => Unit): Future[ServerSession] = {
    sessionInitCrash match {
      case Some(exception) =>
        stateFn(SessionState.SS_DOWNLOADING, None)
        return Future.failed(exception)
      case None => // continue
    }
    val session = new Session(
      initRequest.sessionId,
      initRequest.inputs.size,
      initRequest.outputs.size
    )
    sessions += initRequest.sessionId -> session
    Future {
      stateFn(SessionState.SS_DOWNLOADING, None)
      session
    }
  }

  override def quit(): Future[Unit] = {
    quitted = true
    Future.successful(())
  }
}
