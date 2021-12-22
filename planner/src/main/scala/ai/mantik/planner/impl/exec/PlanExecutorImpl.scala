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
import ai.mantik.componently.utils.FutureHelper
import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.executor.Executor
import ai.mantik.executor.model._
import ai.mantik.planner.PlanExecutor.PlanExecutorException
import ai.mantik.planner.graph.Graph
import ai.mantik.planner.impl.MantikItemStateManager
import ai.mantik.planner.repository.{DeploymentInfo, FileRepository, MantikArtifactRetriever, Repository}
import ai.mantik.planner._
import akka.stream.scaladsl.Sink
import io.circe.syntax._

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

/** Responsible for exeucting Execution Plans using [[Executor]] */
class PlanExecutorImpl @Inject() (
    fileRepository: FileRepository,
    repository: Repository,
    artifactRetriever: MantikArtifactRetriever,
    mantikItemStateManager: MantikItemStateManager,
    uiStateService: UiStateService,
    executor: Executor,
    executionCleanup: ExecutionCleanup
)(implicit akkaRuntime: AkkaRuntime)
    extends ComponentBase
    with PlanExecutor {

  val openFilesBuilder: ExecutionOpenFilesBuilder = new ExecutionOpenFilesBuilder(
    fileRepository
  )

  val basicOpExecutor = new BasicOpExecutor(
    fileRepository,
    repository,
    artifactRetriever,
    mantikItemStateManager
  )

  override def execute[T](plan: Plan[T]): Future[T] = {
    val jobId = UUID.randomUUID().toString
    logger.info(s"Executing job ${jobId}")
    uiStateService.registerNewJob(jobId, plan)
    uiStateService.startJob(jobId)

    val result = executionCleanup.isReady.flatMap { _ =>
      rawExecutePlan(jobId, plan)
    }

    result.andThen {
      case Success(_) => uiStateService.finishJob(jobId)
      case Failure(exception) =>
        uiStateService.finishJob(jobId, Some(Option(exception.getMessage).getOrElse("Unknown error")))
    }
    result
  }

  /** Execute a Plan */
  private def rawExecutePlan[T](jobId: String, plan: Plan[T]): Future[T] = {
    for {
      files <- uiStateService.executingNamedOperation(jobId, UiStateService.PrepareFilesName) {
        openFilesBuilder.openFiles(plan.files)
      }
      memory = new Memory()
      result <- executeOp(jobId, plan.op, Nil)(files, memory)
    } yield result
  }

  /**
    * Execute a single operation
    * @param jobId id of the job
    * @param planOp the operation to execute
    * @param revPath the reverse path to this operation
    */
  def executeOp[T](
      jobId: String,
      planOp: PlanOp[T],
      revPath: List[Int]
  )(implicit files: ExecutionOpenFiles, memory: Memory): Future[T] = {
    uiStateService.executingCoordinatedOperation(jobId, revPath.reverse) {
      try {
        executeOpInner(jobId, planOp, revPath).andThen { case Success(value) =>
          memory.setLast(value)
        }
      } catch {
        case NonFatal(e) =>
          Future.failed(e)
      }
    }
  }

  private def executeOpInner[T](
      jobId: String,
      planOp: PlanOp[T],
      revPath: List[Int]
  )(implicit files: ExecutionOpenFiles, memory: Memory): Future[T] = {
    planOp match {
      case basic: PlanOp.BasicOp[T] =>
        basicOpExecutor.execute(basic)
      case PlanOp.Sequential(parts, last) =>
        val lastOpIndex = parts.length
        FutureHelper
          .time(logger, s"Running ${parts.length} sub tasks") {
            FutureHelper.afterEachOtherStateful(parts.zipWithIndex, memory.getLastOrNull()) { case (_, (part, idx)) =>
              executeOp(jobId, part, idx :: revPath)
            }
          }
          .flatMap { _ =>
            FutureHelper.time(logger, s"Running last part") {
              executeOp(jobId, last, lastOpIndex :: revPath)
            }
          }
      case PlanOp.RunGraph(graph) =>
        runGraph(graph)
      case dp: PlanOp.DeployPipeline =>
        deployPipeline(dp)
    }
  }

  private def runGraph(graph: Graph[PlanNodeService])(
      implicit files: ExecutionOpenFiles
  ): Future[Unit] = {
    val evaluationId = UUID.randomUUID().toString
    for {
      evaluation <- convertGraphToEvaluationWorkload(evaluationId, graph)
      _ <- executor.startEvaluation(EvaluationWorkloadRequest(evaluation))
      result <- trackEvaluation(evaluationId)
    } yield {
      result
    }
  }

  /** Deploys a pipeline including bookkeeping */
  private def deployPipeline(
      dp: PlanOp.DeployPipeline
  )(implicit files: ExecutionOpenFiles): Future[DeploymentState] = {
    for {
      deploymentState <- rawDeployPipeline(dp)
      _ = mantikItemStateManager.update(dp.itemId, _.copy(deployment = Some(deploymentState)))
      deploymentInfo = {
        DeploymentInfo(
          evaluationId = deploymentState.evaluationId,
          internalUrl = deploymentState.internalUrl,
          externalUrl = deploymentState.externalUrl,
          timestamp = akkaRuntime.clock.instant()
        )
      }
      _ <- repository.setDeploymentInfo(dp.itemId, Some(deploymentInfo))
    } yield {
      logger.info(
        s"Deployed pipeline ${dp.serviceId} to ${deploymentInfo.internalUrl} (external=${deploymentInfo.externalUrl})"
      )
      deploymentState
    }
  }

  /** Raw deploys a pipeline (without bookkeeping) */
  private def rawDeployPipeline(
      dp: PlanOp.DeployPipeline
  )(implicit files: ExecutionOpenFiles): Future[DeploymentState] = {
    for {
      request <- buildPipelineDeploymentRequest(dp)
      response <- executor.startDeployment(request)
    } yield {
      DeploymentState(
        evaluationId = request.workload.id,
        internalUrl = response.internalUrl,
        externalUrl = response.externalUrl
      )
    }
  }

  private def buildPipelineDeploymentRequest(
      dp: PlanOp.DeployPipeline
  )(implicit files: ExecutionOpenFiles): Future[EvaluationDeploymentRequest] = {
    val evaluationId = UUID.randomUUID().toString
    val converter = new EvaluationWorkloadConverter(evaluationId, dp.graph, files, fileRepository)
    for {
      builder <- converter.resultBuilder
    } yield {
      val input = WorkloadSessionPort(
        builder.sessions.get(converter.sessionIdName(dp.input.node)).getOrElse {
          throw new PlanExecutorException(s"Invalid deployment plan, input ${dp.input} not found")
        },
        dp.input.port
      )
      val output = WorkloadSessionPort(
        builder.sessions.get(converter.sessionIdName(dp.output.node)).getOrElse {
          throw new PlanExecutorException(s"Invalid deployment plan, output ${dp.output} not found")
        },
        dp.output.port
      )
      EvaluationDeploymentRequest(
        workload = builder.result,
        input = input,
        output = output,
        inputDataType = dp.inputDataType.asJson,
        outputDataType = dp.outputDataType.asJson,
        ingressName = dp.ingress,
        nameHint = dp.serviceNameHint
      )
    }
  }

  private def convertGraphToEvaluationWorkload(
      evaluationId: String,
      graph: Graph[PlanNodeService]
  )(implicit files: ExecutionOpenFiles): Future[EvaluationWorkload] = {
    new EvaluationWorkloadConverter(evaluationId, graph, files, fileRepository).result
  }

  private def trackEvaluation(id: String): Future[Unit] = {
    val source = executor.trackEvaluation(id)
    val sink = Sink.fold[Try[Unit], EvaluationWorkloadState](Success(())) { (current, next) =>
      // TODO: Update UI State
      next.status match {
        case f: WorkloadStatus.Failed => Failure(new PlanExecutorException(f.error))
        case WorkloadStatus.Done      => Success(())
        case _                        => current
      }
    }
    source.runWith(sink).transform(_.flatten)
  }
}
