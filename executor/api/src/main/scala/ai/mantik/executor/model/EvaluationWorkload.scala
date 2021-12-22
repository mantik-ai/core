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
package ai.mantik.executor.model

import ai.mantik.executor.model.docker.Container
import akka.NotUsed
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import io.circe.Json

import scala.concurrent.Future

/**
  * A Workload to be executed by the Executor.
  * @param id id of the workload
  * @param containers list of containers inside the workload.
  * @param sessions the sessions to use
  * @param sources data flowing into the evaluation
  * @param sinks data coming out of the evaluation
  */
case class EvaluationWorkload(
    id: String,
    containers: Vector[WorkloadContainer],
    sessions: Vector[WorkloadSession],
    sources: Vector[DataSource],
    sinks: Vector[DataSink],
    links: Vector[Link]
) {

  /** Return inputs which are not connected via a link. */
  lazy val unconnectedInputs: Vector[WorkloadSessionPort] = {
    val inputs: Vector[WorkloadSessionPort] = for {
      (session, sessionId) <- sessions.zipWithIndex
      port <- session.inputContentTypes.indices
    } yield {
      WorkloadSessionPort(sessionId, port)
    }
    val connectedInputs: Vector[WorkloadSessionPort] = links.collect {
      case Link(_, Right(sessionPort: WorkloadSessionPort)) => sessionPort
    }
    inputs.diff(connectedInputs)
  }

  /** Return outputs which are not connected via a link. */
  lazy val unconnectedOutputs: Vector[WorkloadSessionPort] = {
    val outputs: Vector[WorkloadSessionPort] = for {
      (session, sessionId) <- sessions.zipWithIndex
      port <- session.outputContentTypes.indices
    } yield {
      WorkloadSessionPort(sessionId, port)
    }
    val connectedOutputs: Vector[WorkloadSessionPort] = links.collect {
      case Link(Right(sessionPort: WorkloadSessionPort), _) => sessionPort
    }
    outputs.diff(connectedOutputs)
  }
}

/**
  * A Container inside an evaluation workload.
  * @param container container definition
  * @param nameHint hint to give out as a name.
  */
case class WorkloadContainer(
    container: Container,
    nameHint: Option[String] = None
)

/**
  * A Session to be initialized in one of the containers
  * @param containerId the id of the container (within [[EvaluationWorkload.containers]])
  * @param mnpSessionId the id of the session to initialize (MNP's InitSessionRequest)
  * @param mantikHeader the mantik header to initialize the session
  * @param payload the payload for the session, if there is any
  * @param inputContentTypes input ports of the session with their content types
  * @param outputContentTypes output ports of the session with their content types
  */
case class WorkloadSession(
    containerId: Int,
    mnpSessionId: String,
    mantikHeader: Json,
    payload: Option[Int],
    inputContentTypes: Vector[String],
    outputContentTypes: Vector[String]
)

/**
  * Identifies a port within a session in the workload.
  * (Note: It is context dependent if this is an input ort output port)
  * @param sessionId id of the session (within [[EvaluationWorkload.sessions]]
  * @param port number of the port.
  */
case class WorkloadSessionPort(
    sessionId: Int,
    port: Int
)

/**
  * A Link within the graph.
  * @param from the id of a [[DataSource]] within [[EvaluationWorkload.sources]] or a [[WorkloadSessionPort]]
  * @param to the id of a [[DataSink]] within [[EvaluationWorkload.sinks]] or a [[WorkloadSessionPort]]
  */
case class Link(
    from: Either[Int, WorkloadSessionPort],
    to: Either[Int, WorkloadSessionPort]
)

/**
  * A Source of data going into the workload.
  *
  * @param contentType Content type of the data.
  * @param byteCount size of the data
  * @param source the source for this data.
  */
case class DataSource(
    contentType: String,
    byteCount: Long,
    source: Source[ByteString, NotUsed]
)

object DataSource {

  /** Generate a constant data source. */
  def constant(value: ByteString, contentType: String): DataSource = {
    DataSource(contentType, value.length, Source.single(value))
  }
}

/** A Sink of data going out of the workload. */
case class DataSink(
    // Returns the number of bytes transferred
    sink: Sink[ByteString, Future[Long]]
)
