package ai.mantik.executor.docker
import java.util.UUID

import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.executor.common.LabelConstants
import ai.mantik.executor.docker.api.structures.ListContainerResponseRow
import ai.mantik.executor.docker.api.{DockerApiHelper, DockerClient, DockerOperations}
import ai.mantik.executor.docker.buildinfo.BuildInfo
import ai.mantik.executor.model._
import ai.mantik.executor.model.docker.Container
import ai.mantik.executor.{Errors, Executor}
import cats.implicits._
import javax.inject.{Inject, Provider}

import scala.concurrent.Future
import scala.concurrent.duration._

class DockerExecutor @Inject() (dockerClient: DockerClient, executorConfig: DockerExecutorConfig)(
    implicit akkaRuntime: AkkaRuntime
) extends ComponentBase
    with Executor {

  logger.info("Initializing Docker Executor")
  logger.info(s"Default Repo: ${executorConfig.common.dockerConfig.defaultImageRepository}")
  logger.info(s"Default Tag:  ${executorConfig.common.dockerConfig.defaultImageTag}")
  logger.info(s"Disable Pull: ${executorConfig.common.disablePull}")
  logger.info(s"Docker Host:  ${dockerClient.dockerHost}")

  private val dockerOperations = new DockerOperations(dockerClient)
  private val dockerRootNameGenerator = new DockerRootNameGenerator(dockerClient)

  val extraServices = new ExtraServices(executorConfig, dockerOperations)

  override def publishService(publishServiceRequest: PublishServiceRequest): Future[PublishServiceResponse] = {
    if (publishServiceRequest.port != publishServiceRequest.externalPort) {
      return Future.failed(
        new Errors.BadRequestException("Cannot use different internal/external ports")
      )
    }
    // Nothing to do, services should be reachable directly from docker
    val name = s"${publishServiceRequest.externalName}:${publishServiceRequest.externalPort}"
    Future.successful(
      PublishServiceResponse(
        name
      )
    )

  }

  override def nameAndVersion: Future[String] = {
    dockerClient.version(()).map { dockerVersion =>
      s"DockerExecutor ${BuildInfo.version}  (${BuildInfo.gitVersion}-${BuildInfo.buildNum}) with docker ${dockerVersion.Version} on ${dockerVersion.KernelVersion} "

    }
  }

  override def grpcProxy(isolationSpace: String): Future[GrpcProxy] = {
    // Note: Docker Executor doesn't support one proxy per isolationSpace yet
    val grpcSettings = executorConfig.common.grpcProxy
    extraServices.grpcProxy.map {
      case Some(id) =>
        val url = s"http://${dockerClient.dockerHost}:${grpcSettings.externalPort}"
        GrpcProxy(
          proxyUrl = Some(url)
        )
      case None =>
        if (grpcSettings.enabled) {
          throw new Errors.InternalException("Grpc Proxy not started but enabled?!")
        } else {
          throw new Errors.NotFoundException("Grpc Proxy not enabled")
        }
    }
  }

  override def startWorker(startWorkerRequest: StartWorkerRequest): Future[StartWorkerResponse] = {
    extraServices.workerNetworkId.flatMap { workerNetworkId =>
      dockerRootNameGenerator.reserveWithOptionalPrefix(startWorkerRequest.nameHint) { name =>
        startWorkerImpl(workerNetworkId, name, startWorkerRequest)
      }
    }
  }

  private def startWorkerImpl(
      workerNetworkId: String,
      name: String,
      startWorkerRequest: StartWorkerRequest
  ): Future[StartWorkerResponse] = {
    val internalId = UUID.randomUUID().toString

    val dockerConverter = new DockerConverter(
      executorConfig,
      isolationSpace = startWorkerRequest.isolationSpace,
      internalId = internalId,
      userId = startWorkerRequest.id
    )
    val ingressConverter = startWorkerRequest.ingressName.map { ingressName =>
      IngressConverter(executorConfig, dockerClient.dockerHost, ingressName)
    }

    startWorkerRequest.definition match {
      case definition: MnpWorkerDefinition =>
        logger.info(s"About to start ${name} (${definition.container.image})")
        val converted = dockerConverter.generateWorkerContainer(name, definition.container, Some(workerNetworkId))
        val maybeInitializer = definition.initializer.map { initializer =>
          dockerConverter.generateMnpPreparer(name, initializer, Some(workerNetworkId))
        }

        for {
          _ <- dockerOperations.createAndRunContainer(converted)
          _ <- maybeInitializer.map(runInitializer).sequence
        } yield {
          StartWorkerResponse(
            nodeName = name
          )
        }
      case pipelineDefinition: MnpPipelineDefinition =>
        val converted =
          dockerConverter.generatePipelineContainer(name, pipelineDefinition.definition, Some(workerNetworkId))

        val withIngress = ingressConverter
          .map(_.containerDefinitionWithIngress(converted))
          .getOrElse(converted)

        for {
          _ <- dockerOperations.createAndRunContainer(withIngress)
        } yield {
          StartWorkerResponse(
            nodeName = name,
            externalUrl = ingressConverter.map(_.ingressUrl)
          )
        }
    }
  }

  private def runInitializer(containerDefinition: ContainerDefinition): Future[Unit] = {
    for {
      createResponse <- dockerOperations.createAndRunContainer(containerDefinition)
      _ = logger.info(s"Runninig initializer ${containerDefinition.name}")
      result <- dockerOperations.waitContainer(createResponse.Id, 1.minute /* TODO Configurable */ )
      text <- dockerClient.containerLogs(createResponse.Id, true, true)
    } yield {
      if (result.StatusCode == 0) {
        ()
      } else {
        logger.warn(
          s"Initializer ${containerDefinition.name} failed with status code ${result.StatusCode} and message ${text}"
        )
        throw new Errors.CouldNotExecutePayload(s"Container init failed ${result.StatusCode}")
      }
    }
  }

  override def listWorkers(listWorkerRequest: ListWorkerRequest): Future[ListWorkerResponse] = {
    listWorkerRequest.nameFilter.foreach { name =>
      // TODO
      logger.warn("Filtering for node names is not performance right now.")
    }
    listWorkers(true, listWorkerRequest.isolationSpace, listWorkerRequest.idFilter).map { response =>
      val workers = response.flatMap(decodeListWorkerResponse(_, listWorkerRequest.nameFilter))
      ListWorkerResponse(
        workers
      )
    }
  }

  private def listWorkers(
      all: Boolean,
      isolationSpace: String,
      userIdFilter: Option[String]
  ): Future[Vector[ListContainerResponseRow]] = {
    val labelFilters = Seq(
      DockerConstants.IsolationSpaceLabelName -> isolationSpace,
      LabelConstants.ManagedByLabelName -> LabelConstants.ManagedByLabelValue,
      LabelConstants.RoleLabelName -> LabelConstants.role.worker
    ) ++ userIdFilter.map { id =>
      LabelConstants.UserIdLabelName -> id
    }
    dockerOperations.listContainers(
      all,
      labelFilters
    )
  }

  private def decodeListWorkerResponse(
      listContainerResponseRow: ListContainerResponseRow,
      nameFilter: Option[String]
  ): Option[ListWorkerResponseElement] = {
    def errorIfEmpty[T](name: String, value: Option[T]): Option[T] = {
      if (value.isEmpty) {
        logger.error(s"Value ${name} is empty in ${listContainerResponseRow.Id}")
      }
      value
    }
    val workerType = listContainerResponseRow.Labels.get(LabelConstants.WorkerTypeLabelName) match {
      case Some(LabelConstants.workerType.mnpWorker)   => WorkerType.MnpWorker
      case Some(LabelConstants.workerType.mnpPipeline) => WorkerType.MnpPipeline
      case unknown =>
        logger.error(s"Missing / unexpected role ${unknown} for ${listContainerResponseRow.Id}")
        WorkerType.MnpWorker
    }
    val maybeIngressName = listContainerResponseRow.Labels.get(DockerConstants.IngressLabelName)
    val externalUrl = maybeIngressName.map { ingressName =>
      IngressConverter(executorConfig, dockerClient.dockerHost, ingressName).ingressUrl
    }
    for {
      rawName <- errorIfEmpty("name", listContainerResponseRow.Names.headOption)
      name = rawName.stripPrefix("/")
      if nameFilter.isEmpty || nameFilter.contains(name)
      userId <- errorIfEmpty("user id label", listContainerResponseRow.Labels.get(LabelConstants.UserIdLabelName))
    } yield {
      ListWorkerResponseElement(
        nodeName = name,
        id = userId,
        container = Some(
          Container(
            image = listContainerResponseRow.Image,
            parameters = listContainerResponseRow.Command.map(_.split(' ').toVector).getOrElse(Nil)
          )
        ),
        state = decodeState(listContainerResponseRow.State, listContainerResponseRow.Status),
        `type` = workerType,
        externalUrl = externalUrl
      )
    }
  }

  private def decodeState(row: ListContainerResponseRow): WorkerState = {
    decodeState(row.State, row.Status)
  }

  private def decodeState(state: String, status: Option[String]): WorkerState = {
    state match {
      case "created" => WorkerState.Pending
      case "running" => WorkerState.Running
      case "restarting" =>
        logger.warn("Found status code restarting?")
        WorkerState.Pending // can this happen?
      case "paused" =>
        logger.warn("Found status code paused?")
        WorkerState.Running // can this happen?
      case "exited" =>
        val errorCode = status.flatMap(DockerApiHelper.decodeStatusCodeFromStatus).getOrElse(0)
        if (errorCode == 0) {
          WorkerState.Succeeded
        } else {
          WorkerState.Failed(errorCode)
        }
      case "dead" => WorkerState.Failed(255)
      case other =>
        logger.warn(s"Found unexpected state ${state}")
        WorkerState.Running
    }
  }

  override def stopWorker(stopWorkerRequest: StopWorkerRequest): Future[StopWorkerResponse] = {
    // go through all workers, if we are going to remove them
    val all = stopWorkerRequest.remove.value
    listWorkers(all, stopWorkerRequest.isolationSpace, stopWorkerRequest.idFilter).flatMap { containers =>
      val decoded: Vector[(StopWorkerResponseElement, ListContainerResponseRow)] = containers.flatMap { row =>
        decodeListWorkerResponse(row, stopWorkerRequest.nameFilter).map { d =>
          StopWorkerResponseElement(
            id = d.id,
            name = d.nodeName
          ) -> row
        }
      }

      val byInternalId: Map[Option[String], Vector[ListContainerResponseRow]] = containers.groupBy { row =>
        row.Labels.get(LabelConstants.InternalIdLabelName)
      }

      val associated = decoded.flatMap { case (_, row) =>
        byInternalId.get(row.Labels.get(LabelConstants.InternalIdLabelName))
      }.flatten

      logger.info(s"Going to stop ${decoded.size} containers")

      Future
        .sequence(associated.map { row =>
          if (stopWorkerRequest.remove.value) {
            // no need to kill before, we are forcing removal
            dockerClient.removeContainer(row.Id, true)
          } else {
            if (decodeState(row) == WorkerState.Running) {
              dockerClient.killContainer(row.Id)
            } else {
              Future.successful(())
            }
          }
        })
        .map { _ =>
          StopWorkerResponse(
            decoded.map(_._1)
          )
        }
    }
  }
}

class DockerExecutorProvider @Inject() (implicit akkaRuntime: AkkaRuntime) extends Provider[DockerExecutor] {
  override def get(): DockerExecutor = {
    val dockerExecutorConfig = DockerExecutorConfig.fromTypesafeConfig(akkaRuntime.config)
    val dockerClient = new DockerClient()
    new DockerExecutor(dockerClient, dockerExecutorConfig)
  }
}
