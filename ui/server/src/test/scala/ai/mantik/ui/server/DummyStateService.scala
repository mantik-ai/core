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
package ai.mantik.ui.server

import ai.mantik.componently.collections.{DiGraph, DiLink}
import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.ui.StateService
import ai.mantik.ui.model.{
  Job,
  JobHeader,
  JobResponse,
  JobState,
  JobsResponse,
  MetricsResponse,
  Operation,
  OperationDefinition,
  OperationId,
  OperationState,
  RunGraphLink,
  RunGraphNode,
  RunGraphResponse,
  SettingEntry,
  SettingsResponse,
  VersionResponse
}
import ai.mantik.ui.server.DummyStateService.jobId
import io.circe.Json
import io.circe.syntax._

import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._

class DummyStateService(implicit akkaRuntime: AkkaRuntime) extends ComponentBase with StateService {
  override def version: VersionResponse = VersionResponse(
    version = "123.4",
    scalaVersion = "2.12-superduper",
    executorBackend = "Kubernetes"
  )

  override def settings: SettingsResponse = SettingsResponse(
    values = Vector(
      SettingEntry("setting1", "foo".asJson),
      SettingEntry("setting2", 123.asJson)
    )
  )

  override def metrics: MetricsResponse = MetricsResponse(
    metrics = Map(
      "count" -> 1.asJson,
      "count2" -> 2.asJson
    )
  )

  var jobsVersion: Long = 1
  var jobVersion: Long = 1

  val jobHeader = JobHeader(
    id = jobId,
    name = None,
    error = None,
    registration = Instant.parse("2021-06-03T13:15:13Z"),
    state = JobState.Running,
    start = Some(Instant.parse("2021-06-03T13:50:13Z")),
    end = None
  )

  override def jobs(pollVersion: Option[Long]): Future[JobsResponse] = {
    pollVersion match {
      case None =>
        Future.successful(
          JobsResponse(
            Vector(jobHeader),
            jobsVersion
          )
        )
      case Some(version) =>
        registerFakeLp {
          jobsVersion += 1
          jobs(None)
        }
    }
  }

  private val lpPromises = new ConcurrentLinkedQueue[Promise[_]]()

  addShutdownHook {
    Future {
      import scala.jdk.CollectionConverters._
      val e = new RuntimeException(s"System is going on")
      lpPromises.asScala.foreach(_.tryFailure(e))
    }
  }

  /** Helper for implementing Fake LP calls */
  private def maybeFakeLp[T](pollVersion: Option[Long])(f: => T): Future[T] = {
    pollVersion match {
      case None    => Future.successful(f)
      case Some(_) => registerFakeLp(Future.successful(f))
    }
  }

  private def registerFakeLp[T](f: => Future[T]): Future[T] = {
    val promise = Promise[T]()
    lpPromises.add(promise)

    actorSystem.scheduler.scheduleOnce(10.seconds) {
      promise.completeWith(f)
      lpPromises.remove(promise)
    }

    promise.future
  }

  override def job(id: String, pollVersion: Option[Long]): Future[JobResponse] = {
    if (id != jobId) {
      Future.failed(new StateService.EntryNotFoundException("Not found"))
    } else {
      maybeFakeLp(pollVersion) {
        jobVersion += 1
        JobResponse(
          Job(
            jobHeader,
            Seq(
              Operation(
                OperationId(Right(List(1, 2, 3))),
                state = OperationState.Running,
                start = Some(Instant.now()),
                end = None,
                definition = OperationDefinition.RunGraph()
              ),
              Operation(
                OperationId(Right(List(1, 2, 4))),
                state = OperationState.Done,
                start = Some(Instant.now().minusSeconds(3)),
                end = Some(Instant.now()),
                definition = OperationDefinition.Other("Sample2")
              ),
              Operation(
                OperationId(Right(List(1, 2, 5))),
                state = OperationState.Failed,
                start = Some(Instant.now().minusSeconds(73)),
                end = Some(Instant.now()),
                definition = OperationDefinition.Other("Sample3"),
                error = Some("Something bad happened")
              ),
              Operation(
                OperationId(Left("special")),
                state = OperationState.Pending,
                definition = OperationDefinition.Other("Not implemented")
              )
            )
          ),
          jobVersion
        )
      }
    }
  }

  var graphVersion = 1

  override def runGraph(
      jobId: String,
      operationId: OperationId,
      pollVersion: Option[Long]
  ): Future[RunGraphResponse] = {
    maybeFakeLp(pollVersion) {
      RunGraphResponse(
        graph = DiGraph(
          nodes = Map(
            "1" -> RunGraphNode.FileNode(
              123,
              "application/foo"
            ),
            "2" -> RunGraphNode.MnpNode(
              image = "docker1",
              parameters = Seq("a", "b"),
              mantikHeader = Json.obj(
                "some" -> 1.asJson
              ),
              payloadFileContentType = None,
              embeddedPayloadSize = Some(1234)
            )
          ),
          links = Vector(
            DiLink("1", "2", RunGraphLink(0, 0, "application/mantik", Some(100 + graphVersion)))
          )
        ),
        version = graphVersion
      )
    }
  }
}

object DummyStateService {
  val jobId = "325k-34534o5-3p45"
}
