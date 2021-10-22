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

import ai.mantik.bridge.protocol.bridge.MantikInitConfiguration
import ai.mantik.componently.utils.FutureHelper
import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.executor.model._
import ai.mantik.mnp.protocol.mnp._
import ai.mantik.mnp.{MnpAddressUrl, MnpSession, MnpSessionUrl, SessionInitException}
import ai.mantik.planner.PlanExecutor.PlanExecutorException
import ai.mantik.planner._
import ai.mantik.planner.graph.{Graph, Node}
import ai.mantik.planner.impl.exec.MnpExecutionPreparation.{InputPush, OutputPull}
import ai.mantik.planner.impl.exec.MnpWorkerManager.{ContainerMapping, ReservedContainer}
import ai.mantik.planner.impl.{MantikItemStateManager, Metrics}
import ai.mantik.planner.pipelines.{PipelineRuntimeDefinition, ResolvedPipelineStep}
import ai.mantik.planner.repository.FileRepository.FileGetResult
import ai.mantik.planner.repository._
import akka.util.ByteString
import cats.implicits._
import com.google.protobuf.any.Any
import io.circe.syntax._

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

/** Naive MNP Implementation of an Executor. */
@Singleton
class MnpPlanExecutor @Inject() (
    fileRepository: FileRepository,
    repository: Repository,
    artifactRetriever: MantikArtifactRetriever,
    payloadExecutorProvider: ExecutionPayloadProvider,
    mantikItemStateManager: MantikItemStateManager,
    uiStateService: UiStateService,
    executionCleanup: ExecutionCleanup,
    mnpWorkerManager: MnpWorkerManager,
    metrics: Metrics
)(implicit akkaRuntime: AkkaRuntime)
    extends ComponentBase
    with PlanExecutor {

  /** Session Name inside deployed items. */
  val DeployedSessionName = "deployed"

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

    val result = for {
      _ <- executionCleanup.isReady
      result <- withContainers(jobId, plan) { containerMapping =>
        executeWithContainers(containerMapping, jobId, plan)
      }
    } yield {
      result
    }

    result.andThen {
      case Success(_) => uiStateService.finishJob(jobId)
      case Failure(exception) =>
        uiStateService.finishJob(jobId, Some(Option(exception.getMessage).getOrElse("Unknown error")))
    }
    result
  }

  /** Ensure the existence of containers while f is running, and clean them up afterwards
    * (Note: we won't wait for cleanup, it's done async)
    */
  private def withContainers[T](jobId: String, plan: Plan[T])(f: ContainerMapping => Future[T]): Future[T] = {
    uiStateService
      .executingNamedOperation(jobId, UiStateService.PrepareContainerName) {
        mnpWorkerManager.reserveContainers(jobId, plan)
      }
      .flatMap { containerMapping =>
        val result = f(containerMapping)

        result.andThen { case _ =>
          mnpWorkerManager.closeConnectionAndStopContainers(containerMapping)
        }
      }
  }

  /** Execute a plan with given containers. */
  private def executeWithContainers[T](containerMapping: ContainerMapping, jobId: String, plan: Plan[T]): Future[T] = {
    for {
      files <- uiStateService.executingNamedOperation(jobId, UiStateService.PrepareFilesName) {
        openFilesBuilder.openFiles(plan.files)
      }
      memory = new Memory()
      result <- executeOp(jobId, plan.op, Nil)(files, memory, containerMapping)
    } yield result
  }

  /** Execute a single operation
    * @param jobId id of the job
    * @param planOp the operation to execute
    * @param revPath the reverse path to this operation
    */
  def executeOp[T](
      jobId: String,
      planOp: PlanOp[T],
      revPath: List[Int]
  )(implicit files: ExecutionOpenFiles, memory: Memory, containerMapping: ContainerMapping): Future[T] = {
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
  )(implicit files: ExecutionOpenFiles, memory: Memory, containerMapping: ContainerMapping): Future[T] = {
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
      case da: PlanOp.DeployAlgorithm =>
        deployAlgorithm(da)
      case dp: PlanOp.DeployPipeline =>
        deployPipeline(dp)
    }
  }

  private def runGraph(graph: Graph[PlanNodeService])(
      implicit containerMapping: ContainerMapping,
      files: ExecutionOpenFiles
  ): Future[Unit] = {
    val graphId = UUID.randomUUID().toString
    val containerAddresses: Map[String, MnpAddressUrl] = graph.nodes.collect {
      case (name, Node(c: PlanNodeService.DockerContainer, _, _)) =>
        name -> containerMapping.containers(c.container).mnpAddress
    }
    val taskId = "evaluation"

    withGraphRemoteFiles(graph, files) { remoteFiles =>
      val preparation: MnpExecutionPreparation = new MnpExecutionPreparer(
        graphId,
        graph,
        containerAddresses,
        files,
        remoteFiles
      ).build()

      for {
        sessions <- initializeSessions(graph, containerMapping, preparation)
        progressTracker = new ProgressTracker(sessions, taskId)
        _ <- runLinks(taskId, sessions, preparation)
        _ = progressTracker.stop()
        _ <- shutdownSessions(sessions)
      } yield {
        // Done
        ()
      }
    }
  }

  /** Provides temporary remote files for a graph. Files will be deleted afterwards. */
  private def withGraphRemoteFiles[T](
      graph: Graph[PlanNodeService],
      files: ExecutionOpenFiles
  )(f: Map[String, String] => Future[T]): Future[T] = {
    val fileMapping: Vector[(String, String)] = graph.nodes.collect {
      case (nodeId, Node(d: PlanNodeService.DockerContainer, _, _)) if d.data.isDefined =>
        val fileId = files.resolveFileRead(d.data.get).fileId
        nodeId -> fileId
    }.toVector

    // Tricky, we want remote files to be deleted when something fails

    val t0 = System.currentTimeMillis()
    val uploads = fileMapping.map { case (nodeId, fileId) =>
      payloadExecutorProvider.provideTemporary(fileId)
    }

    manyFuturesWithResult(uploads).flatMap { results =>
      val t1 = System.currentTimeMillis()
      val failed = results.count(_.isFailure)
      logger.debug(s"Uploaded ${uploads.size} temporaries, failed: ${failed} within ${t1 - t0}ms")

      val firstFailure = results.collectFirst { case Failure(err) =>
        err
      }
      val successes: Vector[(String, payloadExecutorProvider.TemporaryFileKey, String)] =
        fileMapping.zip(results).collect { case ((nodeId, _), Success((key, url))) =>
          (nodeId, key, url)
        }

      val inner = if (failed > 0) {
        logger.warn(s"There were ${failed} failed uploads", firstFailure.get)
        Future.failed(new PlanExecutorException(s"There were ${failed} failed uploads", firstFailure.get))
      } else {
        f(successes.map { s =>
          (s._1, s._3)
        }.toMap)
      }

      for {
        result <- inner
        _ <- payloadExecutorProvider.undoTemporary(successes.map(_._2))
      } yield result
    }
  }

  private def manyFuturesWithResult[T](in: Vector[Future[T]]): Future[Vector[Try[T]]] = {
    val asTrials: Vector[Future[Try[T]]] = in.map { future =>
      future
        .map(Success(_))
        .recover { case x => Failure(x) }
    }
    Future.sequence(asTrials)
  }

  /** Helper which tracks and prints progress of tasks within the graph. */
  private class ProgressTracker(nodes: Map[String, MnpSession], taskId: String) {

    private val cancellable = actorSystem.scheduler.scheduleWithFixedDelay(0.second, 5.second) { () => printProgress() }

    private def printProgress(): Unit = {
      val progresses: Future[Vector[(String, String, QueryTaskResponse)]] = nodes.toVector.map {
        case (nodeId, session) =>
          session.task(taskId).query(false).map { queryResponse =>
            (nodeId, session.mnpUrl, queryResponse)
          }
      }.sequence
      progresses.andThen {
        case Failure(err) =>
          logger.warn(s"Could not fetch task status", err)
        case Success(value) =>
          val sorted = value.sortBy(_._1)
          logger.debug(s"Periodic Task Status, size=${sorted.size}")
          sorted.zipWithIndex.foreach { case ((nodeId, mnpUrl, queryResponse), idx) =>
            val error = if (queryResponse.error.nonEmpty) {
              s"Error: ${queryResponse.error}"
            } else ""
            logger.debug(
              s"${idx + 1}/${sorted.size} ${nodeId} ${mnpUrl} ${error} ${queryResponse.state.toString()} " +
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
        s"${s.data}B/${s.msgCount}C${closed}${error}"
      }
      list.map(formatSingle).mkString("[", ",", "]")
    }

    def stop(): Unit = {
      cancellable.cancel()
    }

  }

  private def initializeSessions(
      graph: Graph[PlanNodeService],
      containerMapping: ContainerMapping,
      preparation: MnpExecutionPreparation
  ): Future[Map[String, MnpSession]] = {
    val futures = preparation.sessionInitializers.map { case (nodeId, initializer) =>
      val container = graph.nodes(nodeId).service.asInstanceOf[PlanNodeService.DockerContainer].container
      val reservedContainer = containerMapping.containers(container)
      initializeSession(reservedContainer, initializer).map { session =>
        nodeId -> session
      }
    }
    Future.sequence(futures).map(_.toMap)
  }

  private def initializeSession(
      container: ReservedContainer,
      initializer: MnpExecutionPreparation.SessionInitializer
  ): Future[MnpSession] = {
    logger.debug(
      s"Initializing session ${container.mnpClient.address}/${initializer.sessionId}, ${initializer.config.header}"
    )
    logger.debug(
      s"Associated payload: ${!initializer.config.payload.isEmpty} (contentType: ${initializer.config.payloadContentType})"
    )
    container.mnpClient
      .initSession(
        initializer.sessionId,
        Some(initializer.config),
        initializer.inputPorts,
        initializer.outputPorts
      )
      .recover { case e: SessionInitException =>
        throw new PlanExecutorException(
          s"Could not init MNP session on ${container.mnpAddress} (image=${container.image})",
          e
        )
      }
  }

  private def runLinks(
      taskId: String,
      sessions: Map[String, MnpSession],
      preparation: MnpExecutionPreparation
  ): Future[Unit] = {
    // TODO: The FileService should copy the files to and from MNP, not the Executor.
    // However in this early stage they are the same process anyway.
    val inputPushFutures = preparation.inputPushs.map { inputPush =>
      runInputPush(taskId, sessions(inputPush.nodeId), inputPush)
    }
    val outputPullFutures = preparation.outputPulls.map { outputPull =>
      runOutputPull(taskId, sessions(outputPull.nodeId), outputPull)
    }
    val queryTaskFutures = preparation.taskQueries.map { taskQuery =>
      val session = sessions(taskQuery.nodeId)
      logger.debug(s"Sending Query Task to ${session.mnpUrl}/${taskId}")
      session.task(taskId).query(true)
    }
    Future.sequence(inputPushFutures ++ outputPullFutures ++ queryTaskFutures).map { _ =>
      ()
    }
  }

  private def runInputPush(taskId: String, session: MnpSession, inputPush: InputPush): Future[Unit] = {
    logger.debug(
      s"Starting push from ${inputPush.fileGetResult.fileId} to ${session.mnpUrl}/${taskId}/${inputPush.portId}"
    )
    fileRepository
      .loadFile(inputPush.fileGetResult.fileId)
      .flatMap { result =>
        val runTask = session.task(taskId)
        val sink = runTask.push(inputPush.portId)
        result.source
          .map { bytes =>
            metrics.mnpPushBytes.inc(bytes.length)
            bytes
          }
          .runWith(sink)
      }
      .map { case (bytes, response) =>
        logger.debug(
          s"Pushed ${bytes} from ${inputPush.fileGetResult.fileId} to ${session.mnpUrl}/${taskId}/${inputPush.portId}"
        )
      }
  }

  private def runOutputPull(taskId: String, session: MnpSession, outputPull: OutputPull): Future[Unit] = {
    logger.debug(
      s"Starting pull from ${session.mnpUrl}/${taskId}/${outputPull.portId} to ${outputPull.fileStorageResult.fileId}"
    )
    fileRepository
      .storeFile(outputPull.fileStorageResult.fileId)
      .flatMap { fileSink =>
        val runTask = session.task(taskId)
        val source = runTask
          .pull(outputPull.portId)
          .map { bytes =>
            metrics.mnpPullBytes.inc(bytes.length)
            bytes
          }
        source.runWith(fileSink)
      }
      .map { bytes =>
        logger.debug(
          s"Pulled ${bytes} from ${session.mnpUrl}/${taskId}/${outputPull.portId} to ${outputPull.fileStorageResult.fileId}"
        )
      }
  }

  private def shutdownSessions(sessions: Map[String, MnpSession]): Future[Unit] = {
    Future
      .sequence(
        sessions.map { case (_, session) =>
          session.quit()
        }
      )
      .map(_ => ())
  }

  private def deployAlgorithm(
      deployAlgorithm: PlanOp.DeployAlgorithm
  )(implicit files: ExecutionOpenFiles): Future[DeploymentState] = {
    val payloadData = deployAlgorithm.node.service.data.map(files.resolveFileRead)
    payloadExecutorProvider.providePermanent(deployAlgorithm.item.itemId).flatMap { payloadUrl =>
      val initCall = ByteString(buildInitCallForDeployment(deployAlgorithm.node, payloadData, payloadUrl).toByteArray)
      for {
        response <- mnpWorkerManager.runPermanentWorker(
          deployAlgorithm.serviceId,
          deployAlgorithm.serviceNameHint,
          deployAlgorithm.node.service.container,
          initCall
        )
        mnpUrl = MnpAddressUrl.parse(response.internalUrl).getOrElse {
          throw new PlanExecutorException(s"Could not parse internal url")
        }
        deploymentState = DeploymentState(
          name = response.nodeName,
          internalUrl = mnpUrl.withSession(DeployedSessionName).toString,
          externalUrl = None
        )
        deploymentInfo = DeploymentInfo(
          name = deploymentState.name,
          internalUrl = deploymentState.internalUrl,
          timestamp = akkaRuntime.clock.instant()
        )
        _ = mantikItemStateManager.upsert(deployAlgorithm.item, _.copy(deployment = Some(deploymentState)))
        _ <- repository.setDeploymentInfo(deployAlgorithm.item.itemId, Some(deploymentInfo))
      } yield {
        deploymentState
      }
    }
  }

  private def buildInitCallForDeployment(
      node: Node[PlanNodeService.DockerContainer],
      payloadData: Option[FileGetResult],
      payloadUrl: Option[String]
  ): InitRequest = {
    val inputPorts: Vector[ConfigureInputPort] = node.inputs.map { inputPort =>
      ConfigureInputPort(inputPort.contentType)
    }
    val outputPorts: Vector[ConfigureOutputPort] = node.outputs.map { outputPort =>
      ConfigureOutputPort(outputPort.contentType)
    }

    val initConfiguration = MantikInitConfiguration(
      header = node.service.mantikHeader.toJson,
      payloadContentType = payloadData.map(_.contentType).getOrElse(""),
      payload = payloadUrl
        .map { url =>
          MantikInitConfiguration.Payload.Url(url)
        }
        .getOrElse(
          MantikInitConfiguration.Payload.Empty
        )
    )

    InitRequest(
      sessionId = DeployedSessionName,
      configuration = Some(Any.pack(initConfiguration)),
      inputs = inputPorts,
      outputs = outputPorts
    )
  }

  private def deployPipeline(dp: PlanOp.DeployPipeline): Future[DeploymentState] = {
    deployPipelineSubNodes(dp).flatMap { subNodes =>
      val subDeploymentStates: Map[String, SubDeploymentState] = subNodes.view.mapValues { subDeploymentResult =>
        val addressUrl = MnpAddressUrl.parse(subDeploymentResult.internalUrl) match {
          case Left(bad) => throw new PlanExecutorException(s"Invalid Mnp URL: ${bad}")
          case Right(ok) => ok
        }
        val sessionUrl = addressUrl.withSession(DeployedSessionName)
        SubDeploymentState(
          name = subDeploymentResult.nodeName,
          internalUrl = sessionUrl.toString
        )
      }.toMap

      val runtimeDefinition = buildPipelineRuntimeDefinition(dp, subDeploymentStates)

      for {
        response <- mnpWorkerManager.runPermanentPipeline(
          dp.serviceId,
          definition = MnpPipelineDefinition(runtimeDefinition.asJson),
          ingressName = dp.ingress,
          nameHint = dp.serviceNameHint
        )
        deploymentState = DeploymentState(
          name = response.nodeName,
          internalUrl = response.internalUrl,
          externalUrl = response.externalUrl,
          sub = subDeploymentStates
        )
        deploymentInfo = DeploymentInfo(
          name = deploymentState.name,
          internalUrl = deploymentState.internalUrl,
          timestamp = akkaRuntime.clock.instant(),
          sub = subDeploymentStates.view.mapValues { s =>
            SubDeploymentInfo(
              name = s.name,
              internalUrl = s.internalUrl
            )
          }.toMap
        )
        _ = mantikItemStateManager.upsert(dp.item, _.copy(deployment = Some(deploymentState)))
        _ <- repository.setDeploymentInfo(dp.item.itemId, Some(deploymentInfo))
      } yield {
        logger.info(
          s"Deployed pipeline ${dp.serviceId} to ${deploymentInfo.internalUrl} (external=${deploymentInfo.externalUrl})"
        )
        deploymentState
      }
    }
  }

  private def deployPipelineSubNodes(dp: PlanOp.DeployPipeline): Future[Map[String, StartWorkerResponse]] = {
    val subRequests: Map[String, Future[StartWorkerResponse]] = dp.sub.map { case (key, subItem) =>
      val initCall = buildInitCallForDeployment(subItem.node, None, None)
      val nameHint = "mantik-sub-" + subItem.node.service.container.simpleImageName
      val workerResponse = mnpWorkerManager.runPermanentWorker(
        id = dp.serviceId,
        nameHint = Some(nameHint),
        container = subItem.node.service.container,
        initializer = ByteString(initCall.toByteArray)
      )
      key -> workerResponse
    }

    Future
      .sequence(subRequests.map { case (key, f) =>
        f.map(result => key -> result)
      })
      .map(_.toMap)
  }

  private def buildPipelineRuntimeDefinition(
      dp: PlanOp.DeployPipeline,
      subDeployments: Map[String, SubDeploymentState]
  ): PipelineRuntimeDefinition = {
    def extractStep(step: ResolvedPipelineStep, stepId: Int): PipelineRuntimeDefinition.Step = {
      step match {
        case ResolvedPipelineStep.AlgorithmStep(algorithm) =>
          val state = mantikItemStateManager.getOrInit(algorithm)
          val url = state.deployment match {
            case None    => throw new Planner.InconsistencyException("Required sub algorithm not deployed")
            case Some(d) => d.internalUrl
          }
          val resultType = algorithm.functionType.output
          PipelineRuntimeDefinition.Step(
            url,
            resultType
          )
        case other =>
          val subState = subDeployments.getOrElse(
            stepId.toString,
            throw new Planner.InconsistencyException(s"Required sub step ${stepId} not deployed")
          )
          val resultType = other.functionType.output
          PipelineRuntimeDefinition.Step(
            subState.internalUrl,
            resultType
          )
      }
    }
    val steps = dp.item.resolved.steps.zipWithIndex.map { case (step, stepId) => extractStep(step, stepId) }
    val inputType = dp.item.functionType.input
    PipelineRuntimeDefinition(
      name = dp.item.mantikId.toString,
      steps,
      inputType = inputType
    )
  }
}
