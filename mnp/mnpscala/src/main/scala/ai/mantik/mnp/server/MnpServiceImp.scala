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
package ai.mantik.mnp.server

import ai.mantik.componently.rpc.{RpcConversions, StreamConversions}
import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.mnp.{MnpClient, MnpException, MnpUrl}
import ai.mantik.mnp.protocol.mnp.MnpServiceGrpc.MnpService
import ai.mantik.mnp.protocol.mnp.{
  AboutResponse,
  InitRequest,
  InitResponse,
  PullRequest,
  PullResponse,
  PushRequest,
  PushResponse,
  QueryTaskRequest,
  QueryTaskResponse,
  QuitRequest,
  QuitResponse,
  QuitSessionRequest,
  QuitSessionResponse,
  SessionState,
  TaskPortStatus,
  TaskState
}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import com.google.protobuf.empty.Empty
import io.grpc.stub.StreamObserver

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicLong, AtomicReference}
import java.util.function
import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

private[server] case class ServerSessionState(
    id: String,
    session: ServerSession,
    initRequest: InitRequest,
    forwardDefinitions: Map[Int, MnpUrl],
    tasks: ConcurrentHashMap[String, ServerTaskState] = new ConcurrentHashMap()
)

private[server] case class ServerTaskState(
    task: ServerTask,
    state: TaskState,
    error: Option[String] = None,
    inputs: Vector[PortStatus],
    outputs: Vector[PortStatus]
)

private[server] class PortStatus {
  val bytes = new AtomicLong()
  val messages = new AtomicInteger()
  val done = new AtomicBoolean()
  val error = new AtomicReference[String]()
  val inUse = new AtomicBoolean()
}

class MnpServiceImp(
    backend: ServerBackend,
    quitHandler: () => Unit
)(implicit akkaRuntime: AkkaRuntime)
    extends ComponentBase
    with MnpService {
  private val sessions = new ConcurrentHashMap[String, ServerSessionState]()

  override def about(request: Empty): Future[AboutResponse] = {
    backend.about()
  }

  override def init(request: InitRequest, responseObserver: StreamObserver[InitResponse]): Unit = {
    val stateFn: (SessionState, Option[String]) => Unit = { (state, error) =>
      if (!state.isSsReady && !state.isSsFailed) {
        responseObserver.onNext(InitResponse(state, error.getOrElse("")))
      }
    }
    // Early check for existance
    if (sessions.containsKey(request.sessionId)) {
      logger.error(s"Session ${request.sessionId} already exists")
      responseObserver.onNext(InitResponse(SessionState.SS_FAILED, "Session already exists"))
      responseObserver.onCompleted()
      return
    }

    val forwarders = request.outputs.zipWithIndex.collect {
      case (output, idx) if output.destinationUrl.nonEmpty =>
        val url = MnpUrl.parse(output.destinationUrl).right.getOrElse {
          throw new MnpException(s"Bad forwarding URL")
        }
        if (url.port.isEmpty) {
          throw new MnpException(s"Forwarding URL is missing port")
        }
        idx -> url
    }.toMap

    backend.init(request, stateFn).onComplete {
      case Success(session) =>
        val state = ServerSessionState(
          request.sessionId,
          session,
          request,
          forwardDefinitions = forwarders
        )
        val existing = Option(sessions.putIfAbsent(request.sessionId, state))
        if (existing.isDefined) {
          session.shutdown().andThen { case _ =>
            logger.error(s"Session ${request.sessionId} already existed, closing new one")
            responseObserver.onNext(InitResponse(SessionState.SS_FAILED, "Session already exists"))
          }
        } else {
          logger.info(s"Session ${request.sessionId} created")
          responseObserver.onNext(InitResponse(SessionState.SS_READY))
          responseObserver.onCompleted()
        }
      case Failure(error) =>
        logger.warn(s"Session ${request.sessionId} failed to start", error)
        responseObserver.onNext(InitResponse(SessionState.SS_FAILED, errorFromThrowable(error)))
        responseObserver.onCompleted()
    }
  }

  override def quit(request: QuitRequest): Future[QuitResponse] = {
    backend.quit().map { _ =>
      QuitResponse()
    } andThen { case result =>
      result.failed.foreach { error =>
        logger.error(s"Error on quit", error)
      }
      quitHandler()
    }
  }

  override def quitSession(request: QuitSessionRequest): Future[QuitSessionResponse] = {
    Option(sessions.get(request.sessionId)) match {
      case None =>
        Future.failed(new MnpException(s"Session doesn't exist"))
      case Some(session) =>
        session.session
          .shutdown()
          .andThen { case _ =>
            sessions.remove(request.sessionId)
          }
          .map { _ =>
            QuitSessionResponse()
          }
    }
  }

  override def push(responseObserver: StreamObserver[PushResponse]): StreamObserver[PushRequest] = {
    StreamConversions.respondMultiInSingleOutWithHeader(
      {
        case e: MnpException => e
        case other =>
          throw new MnpException(s"Push failed: ${other.getMessage}", other)
      },
      responseObserver
    ) { (first, source) =>
      getTask(first.sessionId, first.taskId, true).flatMap { taskState =>
        if (first.port < 0 || first.port >= taskState.inputs.length) {
          throw new MnpException(s"Invalid port, expected [0,${taskState.inputs.length - 1}]")
        }
        val portState = taskState.inputs(first.port)
        if (!portState.inUse.compareAndSet(false, true)) {
          throw new MnpException(s"Port already in use")
        }

        val adaptedSource = source
          .map { singleRequest =>
            portState.messages.incrementAndGet()
            portState.bytes.addAndGet(singleRequest.data.size())
            val converted = RpcConversions.decodeByteString(singleRequest.data)
            converted
          }
          .mapMaterializedValue(_ => NotUsed)

        taskState.task.push(first.port, adaptedSource).map { _ =>
          PushResponse()
        } andThen { case result =>
          portState.done.set(true)
          result.failed.foreach { failure =>
            portState.error.set(errorFromThrowable(failure))
          }
        }
      }
    }
  }

  override def pull(request: PullRequest, responseObserver: StreamObserver[PullResponse]): Unit = {
    getTask(request.sessionId, request.taskId, ensure = true).andThen {
      case Failure(exception) => responseObserver.onError(exception)
      case Success(taskState) =>
        pullRun(taskState, request.port) match {
          case Failure(bad) =>
            responseObserver.onError(bad)
          case Success(source) =>
            source
              .runForeach { data =>
                responseObserver.onNext(
                  PullResponse(
                    data = RpcConversions.encodeByteString(data)
                  )
                )
              }
              .andThen {
                case Success(_) =>
                  responseObserver.onCompleted()
                case Failure(err) =>
                  responseObserver.onError(err)
              }
        }
    }
  }

  private def pullRun(taskState: ServerTaskState, port: Int): Try[Source[ByteString, NotUsed]] = {
    if (port < 0 || port >= taskState.outputs.length) {
      Failure(new MnpException(s"Invalid port, expected [0,${taskState.outputs.length - 1}]"))
    } else {
      val portState = taskState.outputs(port)
      if (!portState.inUse.compareAndSet(false, true)) {
        Failure(new MnpException(s"Port already in use"))
      } else {
        val source = taskState.task.pull(port)
        val tapping = Sink
          .foreach[ByteString] { data =>
            portState.messages.incrementAndGet()
            portState.bytes.addAndGet(data.length)
          }
          .mapMaterializedValue { doneFuture =>
            doneFuture.andThen {
              case Failure(exception) =>
                portState.error.set(errorFromThrowable(exception))
                portState.done.set(true)
              case Success(_) =>
                portState.done.set(true)
            }
          }
        val tapped = source.wireTap(tapping)
        Success(tapped)
      }
    }
  }

  private def getTask(sessionId: String, taskId: String, ensure: Boolean): Future[ServerTaskState] = {
    Option(sessions.get(sessionId)) match {
      case None => Future.failed(new MnpException(s"Session ${sessionId} not found"))
      case Some(sessionState) =>
        Option(sessionState.tasks.get(taskId)) match {
          case None =>
            if (ensure) {
              sessionState.session.runTask(taskId).map { task =>
                val taskState = ServerTaskState(
                  task,
                  TaskState.TS_EXISTS,
                  inputs = Vector.fill(sessionState.initRequest.inputs.size)(new PortStatus),
                  outputs = Vector.fill(sessionState.initRequest.outputs.size)(new PortStatus)
                )
                Option(sessionState.tasks.putIfAbsent(taskId, taskState)) match {
                  case Some(existant) =>
                    logger.info(s"Race condition on creating task $sessionId/${taskId}")
                    task.shutdown()
                    existant
                  case None =>
                    startForwarders(sessionState, taskId, taskState)
                    task.finished.andThen { case result =>
                      onTaskFinish(sessionId, taskId, result)
                    }
                    taskState
                }
              }
            } else {
              Future.failed(new MnpException(s"Task ${taskId} not found"))
            }
          case Some(taskState) =>
            Future.successful(taskState)
        }
    }
  }

  private def startForwarders(sessionState: ServerSessionState, taskId: String, task: ServerTaskState): Unit = {
    sessionState.forwardDefinitions.foreach { case (outPortNum, destination) =>
      Future {
        val (channel, client) = MnpClient.connect(destination.address)
        try {
          val source = pullRun(task, outPortNum).getOrElse {
            throw new IllegalStateException(s"Could not issue forwarding pull run")
          }

          val clientSession = client.joinSession(destination.session)
          val sink = clientSession
            .task(taskId)
            .push(destination.port.getOrElse(throw new IllegalStateException(s"No Session in MNP URL")))
          source.runWith(sink).andThen { case result =>
            channel.shutdownNow()
          }
        } catch {
          case NonFatal(e) =>
            channel.shutdownNow()
        }
      }
    }
  }

  /** Update a task state, returns true if found and updated */
  private def updateTaskState(sessionId: String, taskId: String)(f: ServerTaskState => ServerTaskState): Boolean = {
    Option(sessions.get(sessionId)) match {
      case None =>
        false
      case Some(session) =>
        val updated = Option(
          session.tasks.computeIfPresent(
            taskId,
            new function.BiFunction[String, ServerTaskState, ServerTaskState] {
              override def apply(t: String, u: ServerTaskState): ServerTaskState = {
                f(u)
              }
            }
          )
        )
        updated.isDefined
    }
  }

  private def onTaskFinish(sessionId: String, taskId: String, result: Try[Done]): Unit = {
    val state = if (result.isSuccess) {
      TaskState.TS_FINISHED
    } else {
      TaskState.TS_FAILED
    }
    updateTaskState(sessionId, taskId) { task =>
      task.copy(
        state = state,
        error = maybeError(result)
      )
    }
  }

  override def queryTask(request: QueryTaskRequest): Future[QueryTaskResponse] = {
    getTask(request.sessionId, request.taskId, request.ensure) map { task =>
      QueryTaskResponse(
        state = task.state,
        error = task.error.getOrElse(""),
        inputs = task.inputs.map(convertTaskPortStatus),
        outputs = task.outputs.map(convertTaskPortStatus)
      )
    }
  }

  private def convertTaskPortStatus(portStatus: PortStatus): TaskPortStatus = {
    TaskPortStatus(
      msgCount = portStatus.messages.get(),
      data = portStatus.bytes.get(),
      error = Option(portStatus.error.get()).getOrElse(""),
      done = portStatus.done.get()
    )
  }

  private def maybeError(t: Try[_]): Option[String] = {
    t.failed.map(errorFromThrowable).toOption
  }

  private def errorFromThrowable(t: Throwable): String = {
    Option(t.getMessage).getOrElse("Unknown Error")
  }
}
