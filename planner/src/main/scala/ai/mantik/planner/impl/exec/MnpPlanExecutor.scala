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

import java.util.UUID
import ai.mantik.bridge.protocol.bridge.MantikInitConfiguration
import ai.mantik.componently.utils.FutureHelper
import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.executor.Executor
import ai.mantik.executor.model.docker.{Container, DockerConfig}
import ai.mantik.executor.model._
import ai.mantik.mnp.protocol.mnp._
import ai.mantik.mnp.{MnpClient, MnpSession, SessionInitException}
import ai.mantik.planner
import ai.mantik.planner.PlanExecutor.PlanExecutorException
import ai.mantik.planner.graph.{Graph, Node}
import ai.mantik.planner.impl.MantikItemStateManager
import ai.mantik.planner.impl.exec.MnpExecutionPreparation.{InputPush, OutputPull}
import ai.mantik.planner.pipelines.{PipelineRuntimeDefinition, ResolvedPipelineStep}
import ai.mantik.planner.repository._
import ai.mantik.planner._
import ai.mantik.planner.repository.FileRepository.FileGetResult
import akka.http.scaladsl.model.Uri
import akka.util.ByteString
import cats.implicits._
import com.google.protobuf.any.Any
import io.circe.syntax._
import io.grpc.ManagedChannel

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

/** Naive MNP Implementation of an Executor. */
class MnpPlanExecutor(
    fileRepository: FileRepository,
    repository: Repository,
    executor: Executor,
    isolationSpace: String,
    dockerConfig: DockerConfig,
    artifactRetriever: MantikArtifactRetriever,
    payloadExecutorProvider: ExecutionPayloadProvider,
    mantikItemStateManager: MantikItemStateManager
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

  @Singleton
  @Inject
  def this(
      fileRepository: FileRepository,
      repository: Repository,
      executor: Executor,
      retriever: MantikArtifactRetriever,
      payloadExecutorProvider: ExecutionPayloadProvider,
      mantikItemStateManager: MantikItemStateManager
  )(implicit akkaRuntime: AkkaRuntime) {
    this(
      fileRepository,
      repository,
      executor,
      isolationSpace = akkaRuntime.config.getString("mantik.planner.isolationSpace"),
      dockerConfig = DockerConfig.parseFromConfig(
        akkaRuntime.config.getConfig("mantik.bridge.docker")
      ),
      retriever,
      payloadExecutorProvider,
      mantikItemStateManager
    )
  }

  override def execute[T](plan: Plan[T]): Future[T] = {
    val jobId = UUID.randomUUID().toString
    logger.info(s"Executing job ${jobId}")
    for {
      grpcProxy <- executor.grpcProxy(isolationSpace)
      containerMapping <- reserveContainers(grpcProxy, jobId, plan)
      files <- openFilesBuilder.openFiles(plan.files)
      memory = new Memory()
      result <- executeOp(plan.op)(files, memory, containerMapping)
      _ <- stopContainers(jobId)
    } yield {
      result
    }
  }

  def executeOp[T](
      planOp: PlanOp[T]
  )(implicit files: ExecutionOpenFiles, memory: Memory, containerMapping: ContainerMapping): Future[T] = {
    try {
      executeOpInner(planOp).andThen { case Success(value) =>
        memory.setLast(value)
      }
    } catch {
      case NonFatal(e) =>
        Future.failed(e)
    }
  }

  private def executeOpInner[T](
      planOp: PlanOp[T]
  )(implicit files: ExecutionOpenFiles, memory: Memory, containerMapping: ContainerMapping): Future[T] = {
    planOp match {
      case basic: PlanOp.BasicOp[T] =>
        basicOpExecutor.execute(basic)
      case PlanOp.Sequential(parts, last) =>
        FutureHelper
          .time(logger, s"Running ${parts.length} sub tasks") {
            FutureHelper.afterEachOtherStateful(parts, memory.getLastOrNull()) { case (_, part) =>
              executeOp(part)
            }
          }
          .flatMap { _ =>
            FutureHelper.time(logger, s"Running last part") {
              executeOp(last)
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

  private case class ReservedContainer(
      name: String,
      image: String,
      address: String,
      mnpChannel: ManagedChannel,
      mnpClient: MnpClient,
      aboutResponse: AboutResponse
  )

  private case class ContainerMapping(
      containers: Map[Container, ReservedContainer]
  )

  /** Spins up containers needed for a plan. */
  private def reserveContainers[T](grpcProxy: GrpcProxy, jobId: String, plan: Plan[T]): Future[ContainerMapping] = {
    val runGraphs: Vector[PlanOp.RunGraph] = plan.op.foldLeftDown(
      Vector.empty[PlanOp.RunGraph]
    ) {
      case (v, op: PlanOp.RunGraph) => v :+ op
      case (v, _)                   => v
    }

    val containers = (for {
      runGraph <- runGraphs
      (_, node) <- runGraph.graph.nodes
      container <- node.service match {
        case c: PlanNodeService.DockerContainer => Some(c)
        case _                                  => None
      }
    } yield {
      container.container
    }).distinct

    logger.info(s"Spinning up ${containers.size} containers")

    containers
      .traverse { container =>
        startWorker(grpcProxy, jobId, container).map { reservedContainer =>
          container -> reservedContainer
        }
      }
      .map { responses =>
        ContainerMapping(
          responses.toMap
        )
      }
  }

  /** Spin up a container and creates a connection to it. */
  private def startWorker(grpcProxy: GrpcProxy, jobId: String, container: Container): Future[ReservedContainer] = {
    val nameHint = "mantik-" + container.simpleImageName
    val startWorkerRequest = StartWorkerRequest(
      isolationSpace,
      id = jobId,
      definition = MnpWorkerDefinition(
        container = container,
        extraLogins = dockerConfig.logins
      ),
      nameHint = Some(nameHint)
    )
    val t0 = System.currentTimeMillis()
    // TODO: Error Handling, shutting down the worker if the mnpClient fails!
    for {
      response <- executor.startWorker(startWorkerRequest)
      (address, aboutResponse, channel, mnpClient) <- buildConnection(grpcProxy, response.nodeName)
    } yield {
      val t1 = System.currentTimeMillis()
      logger.info(
        s"Spinned up worker ${response.nodeName} image=${container.image} about=${aboutResponse.name} within ${t1 - t0}ms"
      )
      ReservedContainer(
        response.nodeName,
        container.image,
        address,
        channel,
        mnpClient,
        aboutResponse
      )
    }
  }

  /**
    * Build a connection to the container.
    * @return address, about response, mnp channel and mnp client.
    */
  private def buildConnection(
      grpcProxy: GrpcProxy,
      nodeName: String
  ): Future[(String, AboutResponse, ManagedChannel, MnpClient)] = {
    val address = s"${nodeName}:8502" // TODO: Configurable
    Future {
      grpcProxy.proxyUrl match {
        case Some(proxy) => MnpClient.connectViaProxy(proxy, address)
        case None        => MnpClient.connect(address)
      }
    }.flatMap { case (channel, client) =>
      // TODO: Configurable
      FutureHelper
        .tryMultipleTimes(30.seconds, 200.milliseconds) {
          client.about().transform {
            case Success(aboutResponse) => Success(Some(aboutResponse))
            case Failure(e)             => Success(None)
          }
        }
        .map { aboutResponse =>
          (address, aboutResponse, channel, client)
        }
    }
  }

  private def stopContainers(jobId: String): Future[Unit] = {
    executor
      .stopWorker(
        StopWorkerRequest(
          isolationSpace,
          idFilter = Some(jobId)
        )
      )
      .map { _ => () }
  }

  private def runGraph(graph: Graph[PlanNodeService])(
      implicit containerMapping: ContainerMapping,
      files: ExecutionOpenFiles
  ): Future[Unit] = {
    val graphId = UUID.randomUUID().toString
    val containerAddresses: Map[String, String] = graph.nodes.collect {
      case (name, Node(c: PlanNodeService.DockerContainer, _, _)) =>
        name -> containerMapping.containers(c.container).address
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

    private val cancellable = actorSystem.scheduler.schedule(0.second, 5.second) { printProgress() }

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
          s"Could not init MNP session on ${container.address} (image=${container.image})",
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
        result.source.runWith(sink)
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
        val source = runTask.pull(outputPull.portId)
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
      val initCall = buildInitCallForDeployment(deployAlgorithm.node, payloadData, payloadUrl)

      val startWorkerRequest = StartWorkerRequest(
        isolationSpace,
        id = deployAlgorithm.serviceId,
        definition = MnpWorkerDefinition(
          container = deployAlgorithm.node.service.container,
          extraLogins = dockerConfig.logins,
          initializer = Some(ByteString(initCall.toByteArray))
        ),
        nameHint = deployAlgorithm.serviceNameHint,
        keepRunning = true
      )

      for {
        response <- executor.startWorker(startWorkerRequest)
        deploymentState = DeploymentState(
          name = response.nodeName,
          internalUrl = s"mnp://${response.nodeName}:8502/${DeployedSessionName}",
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
      val subDeploymentStates: Map[String, SubDeploymentState] = subNodes.mapValues { subDeploymentResult =>
        SubDeploymentState(
          name = subDeploymentResult.nodeName,
          internalUrl = s"mnp://${subDeploymentResult.nodeName}:8502/${DeployedSessionName}"
        )
      }

      val runtimeDefinition = buildPipelineRuntimeDefinition(dp, subDeploymentStates)

      val startWorkerRequest = StartWorkerRequest(
        isolationSpace,
        id = dp.serviceId,
        definition = MnpPipelineDefinition(
          definition = runtimeDefinition.asJson
        ),
        keepRunning = true,
        ingressName = dp.ingress,
        nameHint = dp.serviceNameHint
      )

      for {
        response <- executor.startWorker(startWorkerRequest)
        deploymentState = DeploymentState(
          name = response.nodeName,
          internalUrl = s"http://${response.nodeName}:8502",
          externalUrl = response.externalUrl,
          sub = subDeploymentStates
        )
        deploymentInfo = DeploymentInfo(
          name = deploymentState.name,
          internalUrl = deploymentState.internalUrl,
          timestamp = akkaRuntime.clock.instant(),
          sub = subDeploymentStates.mapValues { s =>
            SubDeploymentInfo(
              name = s.name,
              internalUrl = s.internalUrl
            )
          }
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
      val startWorkerRequest = StartWorkerRequest(
        isolationSpace = isolationSpace,
        id = dp.serviceId,
        definition = MnpWorkerDefinition(
          container = subItem.node.service.container,
          extraLogins = dockerConfig.logins,
          initializer = Some(ByteString(initCall.toByteArray))
        ),
        keepRunning = true,
        nameHint = Some(nameHint),
        ingressName = None
      )
      key -> executor.startWorker(startWorkerRequest)
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
