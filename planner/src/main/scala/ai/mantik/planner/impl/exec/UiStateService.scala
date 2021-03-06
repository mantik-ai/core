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
import ai.mantik.componently.{AkkaRuntime, ComponentBase, MetricRegistry}
import ai.mantik.executor.Executor
import ai.mantik.planner.Plan
import ai.mantik.planner.buildinfo.BuildInfo
import ai.mantik.ui.StateService
import ai.mantik.ui.model._
import com.codahale.metrics.{Counter, Gauge}
import com.typesafe.config.ConfigValueType
import io.circe.Json
import io.circe.syntax._

import java.util.concurrent.ConcurrentHashMap
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

@Singleton
class UiStateService @Inject() (executor: Executor, metricsService: MetricRegistry)(
    implicit akkaRuntime: AkkaRuntime
) extends ComponentBase
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

  private val workerVersion: Future[String] = executor.nameAndVersion

  override def version: VersionResponse = VersionResponse(
    version = BuildInfo.version,
    scalaVersion = BuildInfo.scalaVersion,
    executorBackend =
      try {
        Await.result(workerVersion, 60.seconds)
      } catch {
        case NonFatal(e) => s"Failed to fetch: ${e.getMessage}"
      }
  )

  override def settings: SettingsResponse = {
    import scala.jdk.CollectionConverters._
    val mantikConfig = config.getConfig("mantik")
    val allValues = mantikConfig
      .entrySet()
      .asScala
      .toVector
      .map { entry =>
        entry.getKey -> entry.getValue
      }
      .sortBy(_._1)

    val converted = allValues.map { case (key, value) =>
      if (key.toLowerCase.contains("password")) {
        SettingEntry(key, "<censored>".asJson)
      } else {
        val convertedEntry = value.valueType() match {
          case _ if key.contains("password") => "<censored>".asJson
          case ConfigValueType.OBJECT        => "object".asJson // should not happen
          case ConfigValueType.LIST          => "list".asJson // can happen
          case ConfigValueType.NUMBER =>
            val doubleCandidate = mantikConfig.getNumber(key).doubleValue()
            if (doubleCandidate.toInt == doubleCandidate) {
              doubleCandidate.toInt.asJson
            } else {
              doubleCandidate.asJson
            }
          case ConfigValueType.BOOLEAN => mantikConfig.getBoolean(key).asJson
          case ConfigValueType.NULL    => Json.Null
          case ConfigValueType.STRING  => mantikConfig.getString(key).asJson
          case _                       => "Unknown Type".asJson
        }
        SettingEntry(key, convertedEntry)
      }
    }

    SettingsResponse(converted)
  }

  override def metrics: MetricsResponse = {
    def formatValue(i: Any): Json = {
      i match {
        case i: Int               => i.asJson
        case i: java.lang.Integer => i.asJson
        case l: Long              => l.asJson
        case l: java.lang.Long    => l.asJson
        case b: Boolean           => b.asJson
        case b: java.lang.Boolean => b.asJson
        case f: Float             => f.asJson
        case f: java.lang.Float   => f.asJson
        case d: Double            => d.asJson
        case d: java.lang.Double  => d.asJson
        case other                => other.toString.asJson
      }
    }
    val formatted = metricsService.all.flatMap { case (key, value) =>
      value match {
        case counter: Counter => Some(key -> counter.getCount.asJson)
        case gauge: Gauge[_]  => Some(key -> formatValue(gauge.getValue))
        case _                => None // Not Supported
      }
    }
    MetricsResponse(formatted)
  }

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
  def registerNewJob(id: String, plan: Plan[_]): Unit = {
    val time = clock.instant()
    val header = JobHeader(
      id = id,
      name = plan.name,
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
  val PrepareFilesName = "prepare_files"
}
