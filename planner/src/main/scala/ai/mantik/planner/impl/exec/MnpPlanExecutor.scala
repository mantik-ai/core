package ai.mantik.planner.impl.exec

import java.util.UUID

import ai.mantik.bridge.protocol.bridge.MantikInitConfiguration
import ai.mantik.componently.utils.FutureHelper
import ai.mantik.componently.{ AkkaRuntime, ComponentBase }
import ai.mantik.ds.element.Bundle
import ai.mantik.executor.Executor
import ai.mantik.executor.model.{ ContainerService, Graph, GrpcProxy, Node, ResourceType, StartWorkerRequest, StopWorkerRequest }
import ai.mantik.executor.model.docker.{ Container, DockerConfig }
import ai.mantik.mnp.{ MnpClient, MnpSession, SessionInitException }
import ai.mantik.mnp.protocol.mnp.{ AboutResponse, ConfigureInputPort, ConfigureOutputPort }
import ai.mantik.planner.PlanExecutor.PlanExecutorException
import ai.mantik.planner.PlanNodeService.DockerContainer
import ai.mantik.planner.Planner.InconsistencyException
import ai.mantik.planner.impl.exec.MnpExecutionPreparation.{ InputPush, OutputPull }
import ai.mantik.planner.repository.{ FileRepository, MantikArtifact, MantikArtifactRetriever, Repository }
import ai.mantik.planner.{ CacheKey, ClientConfig, Plan, PlanExecutor, PlanNodeService, PlanOp }
import akka.http.scaladsl.model.Uri
import javax.inject.{ Inject, Singleton }
import cats.implicits._
import io.grpc.ManagedChannel

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }
import scala.util.control.NonFatal

/** Naive MNP Implementation of an Executor. */
class MnpPlanExecutor(
    fileRepository: FileRepository,
    repository: Repository,
    executor: Executor,
    isolationSpace: String,
    dockerConfig: DockerConfig,
    artifactRetriever: MantikArtifactRetriever,
    fileCache: FileCache,
    clientConfig: ClientConfig
)(implicit akkaRuntime: AkkaRuntime) extends ComponentBase with PlanExecutor {

  val openFilesBuilder: ExecutionOpenFilesBuilder = new ExecutionOpenFilesBuilder(
    fileRepository,
    fileCache
  )
  val basicOpExecutor = new BasicOpExecutor(
    fileRepository,
    repository,
    artifactRetriever,
    fileCache
  )

  @Singleton
  @Inject
  def this(
    fileRepository: FileRepository,
    repository: Repository,
    executor: Executor,
    retriever: MantikArtifactRetriever,
    fileCache: FileCache,
    clientConfig: ClientConfig
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
      fileCache,
      clientConfig
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

  def executeOp[T](planOp: PlanOp[T])(implicit
    files: ExecutionOpenFiles,
    memory: Memory,
    containerMapping: ContainerMapping
  ): Future[T] = {
    try {
      executeOpInner(planOp).andThen {
        case Success(value) => memory.setLast(value)
      }
    } catch {
      case NonFatal(e) =>
        Future.failed(e)
    }
  }

  private def executeOpInner[T](planOp: PlanOp[T])(implicit
    files: ExecutionOpenFiles,
    memory: Memory,
    containerMapping: ContainerMapping): Future[T] = {
    planOp match {
      case basic: PlanOp.BasicOp[T] =>
        basicOpExecutor.execute(basic)
      case PlanOp.Sequential(parts, last) =>
        FutureHelper.time(logger, s"Running ${parts.length} sub tasks") {
          FutureHelper.afterEachOtherStateful(parts, memory.getLastOrNull()) {
            case (_, part) =>
              executeOp(part)
          }
        }.flatMap { _ =>
          FutureHelper.time(logger, s"Running last part") {
            executeOp(last)
          }
        }
      case PlanOp.RunGraph(graph) =>
        runGraph(graph)
      case da: PlanOp.DeployAlgorithm =>
        // TODO
        ???
      case dp: PlanOp.DeployPipeline =>
        // TODO
        ???
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

    containers.traverse { container =>
      startWorker(grpcProxy, jobId, container).map { reservedContainer =>
        container -> reservedContainer
      }
    }.map { responses =>
      ContainerMapping(
        responses.toMap
      )
    }
  }

  /** Spin up a container and creates a connection to it. */
  private def startWorker(grpcProxy: GrpcProxy, jobId: String, container: Container): Future[ReservedContainer] = {
    val startWorkerRequest = StartWorkerRequest(
      isolationSpace,
      id = jobId,
      container = container,
      extraLogins = dockerConfig.logins
    )
    val t0 = System.currentTimeMillis()
    // TODO: Error Handling, shutting down the worker if the mnpClient fails!
    for {
      response <- executor.startWorker(startWorkerRequest)
      (address, aboutResponse, channel, mnpClient) <- buildConnection(grpcProxy, response.nodeName)
    } yield {
      val t1 = System.currentTimeMillis()
      logger.info(s"Spinned up worker ${response.nodeName} image=${container.image} about=${aboutResponse.name} within ${t1 - t0}ms")
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
  private def buildConnection(grpcProxy: GrpcProxy, nodeName: String): Future[(String, AboutResponse, ManagedChannel, MnpClient)] = {
    val address = s"${nodeName}:8502" // TODO: Configurable
    Future {
      grpcProxy.proxyUrl match {
        case Some(proxy) => MnpClient.connectViaProxy(proxy, address)
        case None        => MnpClient.connect(address)
      }
    }.flatMap {
      case (channel, client) =>
        // TODO: Configurable
        FutureHelper.tryMultipleTimes(30.seconds, 200.milliseconds) {
          client.about().transform {
            case Success(aboutResponse) => Success(Some(aboutResponse))
            case Failure(e)             => Success(None)
          }
        }.map { aboutResponse =>
          (address, aboutResponse, channel, client)
        }
    }
  }

  private def stopContainers(jobId: String): Future[Unit] = {
    executor.stopWorker(StopWorkerRequest(
      isolationSpace, idFilter = Some(jobId)
    )).map { _ => () }
  }

  private def runGraph(graph: Graph[PlanNodeService])(
    implicit
    containerMapping: ContainerMapping,
    files: ExecutionOpenFiles
  ): Future[Unit] = {
    val graphId = UUID.randomUUID().toString
    val containerAddresses: Map[String, String] = graph.nodes.collect {
      case (name, Node(c: PlanNodeService.DockerContainer, _)) =>
        name -> containerMapping.containers(c.container).address
    }
    val taskId = "evaluation"
    val preparation: MnpExecutionPreparation = new MnpExecutionPreparer(
      graphId, graph, containerAddresses, files, clientConfig.remoteFileRepositoryAddress
    ).build()

    for {
      sessions <- initializeSessions(graph, containerMapping, preparation)
      _ <- runLinks(taskId, sessions, preparation)
      _ <- shutdownSessions(sessions)
    } yield {
      // Done
      ()
    }
  }

  private def initializeSessions(
    graph: Graph[PlanNodeService],
    containerMapping: ContainerMapping,
    preparation: MnpExecutionPreparation
  ): Future[Map[String, MnpSession]] = {
    val futures = preparation.sessionInitializers.map {
      case (nodeId, initializer) =>
        val container = graph.nodes(nodeId).service.asInstanceOf[PlanNodeService.DockerContainer].container
        val reservedContainer = containerMapping.containers(container)
        initializeSession(reservedContainer, initializer).map { session =>
          nodeId -> session
        }
    }
    Future.sequence(futures).map(_.toMap)
  }

  private def initializeSession(container: ReservedContainer, initializer: MnpExecutionPreparation.SessionInitializer): Future[MnpSession] = {
    logger.debug(s"Initializing session ${container.mnpClient.address}/${initializer.sessionId}, ${initializer.config.header}")
    logger.debug(s"Associated payload: ${initializer.config.payload} (contentType: ${initializer.config.payloadContentType})")
    container.mnpClient.initSession(
      initializer.sessionId,
      Some(initializer.config),
      initializer.inputPorts,
      initializer.outputPorts
    ).recover {
        case e: SessionInitException =>
          throw new PlanExecutorException(s"Could not init MNP session on ${container.address} (image=${container.image})", e)
      }
  }

  private def runLinks(taskId: String, sessions: Map[String, MnpSession], preparation: MnpExecutionPreparation): Future[Unit] = {
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
      session.runTask(taskId).query(true)
    }
    Future.sequence(inputPushFutures ++ outputPullFutures ++ queryTaskFutures).map {
      _ => ()
    }
  }

  private def runInputPush(taskId: String, session: MnpSession, inputPush: InputPush): Future[Unit] = {
    logger.debug(s"Starting push from ${inputPush.fileGetResult.fileId} to ${session.mnpUrl}/${taskId}/${inputPush.portId}")
    fileRepository.loadFile(inputPush.fileGetResult.fileId).flatMap {
      case (contentType, fileSource) =>
        val runTask = session.runTask(taskId)
        val sink = runTask.push(inputPush.portId)
        fileSource.runWith(sink)
    }.map {
      case (bytes, response) =>
        logger.debug(s"Pushed ${bytes} from ${inputPush.fileGetResult.fileId} to ${session.mnpUrl}/${taskId}/${inputPush.portId}")
    }
  }

  private def runOutputPull(taskId: String, session: MnpSession, outputPull: OutputPull): Future[Unit] = {
    logger.debug(s"Starting pull from ${session.mnpUrl}/${taskId}/${outputPull.portId} to ${outputPull.fileStorageResult.fileId}")
    fileRepository.storeFile(outputPull.fileStorageResult.fileId, outputPull.contentType).flatMap { fileSink =>
      val runTask = session.runTask(taskId)
      val source = runTask.pull(outputPull.portId)
      source.runWith(fileSink)
    }.map { bytes =>
      logger.debug(s"Pulled ${bytes} from ${session.mnpUrl}/${taskId}/${outputPull.portId} to ${outputPull.fileStorageResult.fileId}")
    }
  }

  private def shutdownSessions(sessions: Map[String, MnpSession]): Future[Unit] = {
    Future.sequence(
      sessions.map {
        case (_, session) =>
          session.quit()
      }
    ).map(_ => ())
  }

  override private[mantik] def cachedFile(cacheKey: CacheKey): Option[String] = {
    // still necessary?
    fileCache.get(cacheKey)
  }
}
