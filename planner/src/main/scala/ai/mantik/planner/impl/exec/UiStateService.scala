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

import ai.mantik.componently.utils.{Tracked, TrackingContext}
import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.elements.{MantikId, NamedMantikId}
import ai.mantik.executor.Executor
import ai.mantik.planner.PlanOp.AddMantikItem
import ai.mantik.planner.{Plan, PlanFile, PlanFileReference, PlanOp}
import ai.mantik.planner.buildinfo.BuildInfo
import ai.mantik.ui.StateService
import ai.mantik.ui.model.{
  Job,
  JobHeader,
  JobResponse,
  JobState,
  JobsResponse,
  Operation,
  OperationDefinition,
  OperationId,
  OperationState,
  RunGraph,
  RunGraphResponse,
  VersionResponse
}

import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiFunction
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future
import scala.collection.JavaConverters._
import scala.util.{Failure, Success}
import scala.concurrent.duration._

@Singleton
class UiStateService @Inject() (executor: Executor)(implicit akkaRuntime: AkkaRuntime)
    extends ComponentBase
    with StateService {

  private implicit val trackingContext = new TrackingContext(10.seconds)

  case class JobInfo(
      header: JobHeader,
      operations: Seq[OperationId]
  )

  private val jobMap: ConcurrentHashMap[String, Tracked[JobInfo]] = new ConcurrentHashMap()

  case class JobOperationId(jobId: String, opId: OperationId)

  private val operationMap: ConcurrentHashMap[JobOperationId, Tracked[Operation]] = new ConcurrentHashMap()

  private val runGraphMap: ConcurrentHashMap[JobOperationId, Tracked[RunGraph]] = new ConcurrentHashMap()

  private val trackedJobList = new Tracked[Unit](())

  override def version: VersionResponse = VersionResponse(
    version = BuildInfo.version,
    scalaVersion = BuildInfo.scalaVersion,
    executorBackend = executor.getClass.getSimpleName
  )

  override def job(id: String, pollVersion: Option[Long]): Future[JobResponse] = {
    Option(jobMap.get(id)) match {
      case None => return Future.failed(new StateService.EntryNotFoundException(s"Job ${id} not found"))
      case Some(v) =>
        v.maybeMonitor(pollVersion).map { case (job, version) =>
          val operations = job.operations.flatMap { operationId =>
            Option(operationMap.get(JobOperationId(id, operationId)).value)
          }
          JobResponse(
            Job(
              job.header,
              operations
            ),
            version
          )
        }
    }
  }

  override def jobs(pollVersion: Option[Long]): Future[JobsResponse] = {
    trackedJobList.maybeMonitor(pollVersion).map { case (_, version) =>
      val headers = jobMap
        .values()
        .asScala
        .map(_.value.header)
        .toVector
        .sortBy(_.registration)
      val result = JobsResponse(headers, version)
      result
    }
  }

  override def runGraph(
      jobId: String,
      operationId: OperationId,
      pollVersion: Option[Long]
  ): Future[RunGraphResponse] = {
    val key = JobOperationId(jobId, operationId)
    Option(runGraphMap.get(key)) match {
      case None =>
        println(s"Could not find ${operationId}/${operationId}")
        runGraphMap.keys().asScala.foreach { key =>
          println(s"Candidate ${key}")
        }
        Future.failed(new StateService.EntryNotFoundException(s"Run graph not found"))
      case Some(value) =>
        value.maybeMonitor(pollVersion).map { case (graph, version) =>
          RunGraphResponse(
            graph,
            version
          )
        }
    }
  }

  // Methods for MnpPlanExecutor
  def registerNewJob(id: String, plan: Plan[_], name: Option[String] = None): Unit = {
    val time = clock.instant()
    val header = JobHeader(
      id = id,
      name = name,
      registration = time,
      state = JobState.Pending,
      start = None
    )
    val operations = UiTranslation.translateOperations(plan)
    val operationIds = operations.map(_.id)

    val runGraphs = UiTranslation.translateRunGraphs(plan)

    val jobInfo = new Tracked(JobInfo(header, operationIds))
    jobMap.put(id, jobInfo)

    operations.foreach { op =>
      operationMap.put(JobOperationId(id, op.id), new Tracked(op))
    }

    runGraphs.foreach { case (opId, g) =>
      runGraphMap.put(JobOperationId(id, opId), new Tracked(g))
    }

    trackedJobList.update(identity)
  }

  def startJob(id: String): Unit = {
    val currentTime = clock.instant()
    updateJobHeader(id) { header =>
      header.copy(
        state = JobState.Running,
        start = Some(currentTime)
      )
    }
    trackedJobList.update(identity)
  }

  def finishJob(id: String, error: Option[String] = None): Unit = {
    val currentTime = clock.instant()
    updateJobHeader(id) { header =>
      header.copy(
        state = if (error.isEmpty) JobState.Done else JobState.Failed,
        error = error,
        end = Some(currentTime)
      )
    }
    trackedJobList.update(identity)
  }

  /** Track the execution of an operation identified by coordinates */
  def executingCoordinatedOperation[T](jobId: String, coordinates: List[Int])(f: => Future[T]): Future[T] = {
    executingOp(jobId, OperationId(Right(coordinates)))(f)
  }

  /** Track the execution of an named operation */
  def executingNamedOperation[T](jobId: String, opId: String)(f: => Future[T]): Future[T] = {
    executingOp(jobId, OperationId(Left(opId)))(f)
  }

  /** Track the execution of an operation */
  private def executingOp[T](jobId: String, opId: OperationId)(f: => Future[T]): Future[T] = {
    updateOperation(jobId, opId) { op =>
      op.copy(
        state = OperationState.Running,
        start = Some(clock.instant())
      )
    }

    f.andThen {
      case Success(_) =>
        updateOperation(jobId, opId) { op =>
          op.copy(
            state = OperationState.Done,
            end = Some(clock.instant())
          )
        }
      case Failure(exception) =>
        updateOperation(jobId, opId) { op =>
          op.copy(
            state = OperationState.Failed,
            error = Some(Option(exception.getMessage).getOrElse("Unknown")),
            end = Some(clock.instant())
          )
        }
    }
  }

  private def updateJobHeader(id: String)(f: JobHeader => JobHeader): Unit = {
    val value = Option(jobMap.get(id))
    value.foreach { tracked =>
      tracked.update(x => x.copy(header = f(x.header)))
    }
  }

  private def updateOperation(jobId: String, operationId: OperationId)(f: Operation => Operation): Unit = {
    val operation = Option(operationMap.get(JobOperationId(jobId, operationId)))
    operation.foreach { tracked =>
      tracked.update(f)
    }
    val job = Option(jobMap.get(jobId))
    job.foreach(_.update(identity))
  }
}

object UiStateService {
  // Named Operation names
  val PrepareContainerName = "prepare_containers"
  val PrepareFilesName = "prepare_files"
}
