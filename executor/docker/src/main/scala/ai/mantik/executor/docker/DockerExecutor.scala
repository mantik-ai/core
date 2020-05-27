package ai.mantik.executor.docker
import ai.mantik.componently.{ AkkaRuntime, ComponentBase }
import ai.mantik.executor.docker.api.{ DockerApiHelper, DockerClient, DockerOperations }
import ai.mantik.executor.docker.api.structures.{ CreateNetworkRequest, ListContainerRequestFilter, ListContainerResponseRow }
import ai.mantik.executor.docker.buildinfo.BuildInfo
import ai.mantik.executor.model._
import ai.mantik.executor.model.docker.Container
import ai.mantik.executor.{ Errors, Executor }
import cats.implicits._
import javax.inject.{ Inject, Provider }

import scala.concurrent.Future

class DockerExecutor @Inject() (dockerClient: DockerClient, executorConfig: DockerExecutorConfig)(
    implicit
    akkaRuntime: AkkaRuntime
) extends ComponentBase with Executor {

  logger.info("Initializing Docker Executor")
  logger.info(s"Default Repo: ${executorConfig.common.dockerConfig.defaultImageRepository}")
  logger.info(s"Default Tag:  ${executorConfig.common.dockerConfig.defaultImageTag}")
  logger.info(s"Disable Pull: ${executorConfig.common.disablePull}")
  logger.info(s"Docker Host:  ${dockerClient.dockerHost}")

  private val dockerOperations = new DockerOperations(dockerClient)
  private val stateManagerFuture: Future[DockerStateManager] = DockerStateManager.retryingInitialize(dockerClient)
  private val processRunnerFuture: Future[DockerProcessRunner] = stateManagerFuture.map { stateManager =>
    new DockerProcessRunner(dockerClient, stateManager, dockerOperations)
  }
  private val dockerRootNameGenerator = new DockerRootNameGenerator(dockerClient)

  val extraServices = new ExtraServices(executorConfig, dockerOperations)

  override def schedule(job: Job): Future[String] = {
    val info = DockerStateManager.Info(
      DockerStateManager.CommonInfo(
        isolationSpace = job.isolationSpace
      ),
      DockerStateManager.JobPending()
    )

    generateName(info).map { id =>
      logger.info(s"Scheduling job ${id}")
      val converted = new DockerJobConverter(job, executorConfig, id).dockerJob
      processRunnerFuture.foreach(_.runJob(converted))
      id
    }
  }

  private def generateName(info: DockerStateManager.Info): Future[String] = {
    stateManagerFuture.flatMap { stateManager =>
      dockerRootNameGenerator.reserve { name =>
        val reserved = stateManager.reserve(name, info)
        if (!reserved) {
          throw new IllegalStateException("Could not reserve id")
        }
        Future.successful(name)
      }
    }
  }

  override def status(isolationSpace: String, id: String): Future[JobStatus] = {
    for {
      stateManager <- stateManagerFuture
      fullState = stateManager.get(id)
      state = fullState.map(_.state)
    } yield {
      val jobState = state match {
        case Some(_: DockerStateManager.JobSucceeded) =>
          JobState.Finished
        case Some(_: DockerStateManager.JobFailed) =>
          JobState.Failed
        case Some(_: DockerStateManager.JobRunning) =>
          JobState.Running
        case Some(other) =>
          JobState.Pending
        case None =>
          // Job may still exists from previous executor run, however the docker executor is inherent stateful.
          return Future.failed(
            new Errors.NotFoundException(s"Job ${id} not found")
          )

      }
      JobStatus(jobState)
    }
  }

  override def logs(isolationSpace: String, id: String): Future[String] = {
    stateManagerFuture.flatMap { stateManager =>
      stateManager.get(id).map(_.state) match {
        case None =>
          Future.failed(new Errors.NotFoundException(s"Job not found ${id}"))
        case Some(success: DockerStateManager.JobSucceeded) =>
          Future.successful(success.log.lines.getOrElse("No log available"))
        case Some(failure: DockerStateManager.JobFailed) =>
          val response = s"""Job Failed ${failure.msg}
                            |
                            |${failure.log.lines.getOrElse("No log available")}
                            |""".stripMargin
          Future.successful(response)
        case _ =>
          stillTryLog(id)
      }
    }
  }

  /** Try to fetch a log from a non-failure job. */
  private def stillTryLog(jobId: String): Future[String] = {
    // figure out the job node
    dockerClient.listContainersFiltered(
      true,
      ListContainerRequestFilter.forLabelKeyValue(
        DockerConstants.ManagedByLabelName -> DockerConstants.ManagedByLabelValue,
        DockerConstants.RoleLabelName -> DockerConstants.CoordinatorRole,
        DockerConstants.IdLabelName -> jobId
      )
    ).flatMap {
        case candidates if candidates.isEmpty =>
          Future.failed(new Errors.NotFoundException(s"Job not found ${jobId}"))
        case candidates =>
          val coordinator = candidates.head
          dockerClient.containerLogs(
            coordinator.Id, true, true
          )
      }
  }

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

  override def deployService(deployServiceRequest: DeployServiceRequest): Future[DeployServiceResponse] = {
    val info = DockerStateManager.Info(
      DockerStateManager.CommonInfo(
        isolationSpace = deployServiceRequest.isolationSpace
      ),
      DockerStateManager.ServicePending()
    )
    for {
      _ <- deleteDeployedServices(
        DeployedServicesQuery(deployServiceRequest.isolationSpace, Some(deployServiceRequest.serviceId))
      )
      internalName <- generateName(info)
      processRunner <- processRunnerFuture
      converted = new DockerServiceConverter(executorConfig, internalName, deployServiceRequest, dockerClient.dockerHost).converted
      _ <- processRunner.assembleService(converted)
    } yield {
      logger.info(s"Assembled external service ${internalName}")
      DeployServiceResponse(
        serviceName = internalName,
        url = converted.internalUrl,
        externalUrl = converted.externalUrl
      )
    }
  }

  override def queryDeployedServices(deployedServicesQuery: DeployedServicesQuery): Future[DeployedServicesResponse] = {
    queryServices(deployedServicesQuery).map { services =>
      val rows = services.map {
        case (_, _, installed) =>
          DeployedServicesEntry(installed.userServiceId, installed.internalUrl)
      }
      DeployedServicesResponse(rows)
    }
  }

  /**
   * Queries installed services.
   * Returns id, and state fields.
   */
  private def queryServices(deployedServicesQuery: DeployedServicesQuery): Future[Vector[(String, DockerStateManager.CommonInfo, DockerStateManager.ServiceInstalled)]] = {
    stateManagerFuture.map { stateManager =>
      val services = stateManager.dumpOfType[DockerStateManager.ServiceInstalled]
      for {
        (id, info, si) <- services
        if deployedServicesQuery.isolationSpace == info.isolationSpace
        if deployedServicesQuery.serviceId.forall(_ == si.userServiceId)
      } yield {
        (id, info, si)
      }
    }
  }

  override def deleteDeployedServices(deployedServicesQuery: DeployedServicesQuery): Future[Int] = {
    for {
      services <- queryServices(deployedServicesQuery)
      processRunner <- processRunnerFuture
      responses <- services.map { case (id, _, _) => processRunner.removeService(id) }.sequence
    } yield {
      logger.info(s"Removed ${responses.size} services")
      responses.size
    }
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
    val DefaultLabels = Map(
      DockerConstants.IsolationSpaceLabelName -> startWorkerRequest.isolationSpace,
      DockerConstants.UserIdLabelName -> startWorkerRequest.id,
      DockerConstants.ManagedByLabelName -> DockerConstants.ManagedByLabelValue,
      DockerConstants.TypeLabelName -> DockerConstants.WorkerType
    )

    val dockerConverter = new DockerConverter(executorConfig, DefaultLabels)

    extraServices.workerNetworkId.flatMap { workerNetworkId =>
      dockerRootNameGenerator.reserve { name =>
        logger.info(s"About to start ${name} (${startWorkerRequest.container.image})")
        val converted = dockerConverter.generateNewWorkerContainer(name, startWorkerRequest.id, startWorkerRequest.container, Some(workerNetworkId))
        dockerOperations.createContainer(converted).flatMap { res =>
          logger.info(s"Created ${name} (${startWorkerRequest.container.image})")

          dockerClient.startContainer(res.Id).map { _ =>
            logger.info(s"Started ${name}")
            StartWorkerResponse(
              nodeName = name
            )
          }
        }
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

  private def listWorkers(all: Boolean, isolationSpace: String, userIdFilter: Option[String]): Future[Vector[ListContainerResponseRow]] = {
    val labelFilters = Seq(
      DockerConstants.IsolationSpaceLabelName -> isolationSpace,
      DockerConstants.ManagedByLabelName -> DockerConstants.ManagedByLabelValue,
      DockerConstants.TypeLabelName -> DockerConstants.WorkerType
    ) ++ userIdFilter.map { id =>
        DockerConstants.UserIdLabelName -> id
      }
    dockerOperations.listContainers(
      all,
      labelFilters
    )
  }

  private def decodeListWorkerResponse(listContainerResponseRow: ListContainerResponseRow, nameFilter: Option[String]): Option[ListWorkerResponseElement] = {
    def errorIfEmpty[T](name: String, value: Option[T]): Option[T] = {
      if (value.isEmpty) {
        logger.error(s"Value ${name} is empty in ${listContainerResponseRow.Id}")
      }
      value
    }
    for {
      rawName <- errorIfEmpty("name", listContainerResponseRow.Names.headOption)
      name = rawName.stripPrefix("/")
      if nameFilter.isEmpty || nameFilter.contains(name)
      userId <- errorIfEmpty("user id label", listContainerResponseRow.Labels.get(DockerConstants.UserIdLabelName))
    } yield {
      ListWorkerResponseElement(
        nodeName = name,
        id = userId,
        container = Container(
          image = listContainerResponseRow.Image,
          parameters = listContainerResponseRow.Command.map(_.split(' ').toVector).getOrElse(Nil)
        ),
        state = decodeState(listContainerResponseRow.State, listContainerResponseRow.Status)
      )
    }
  }

  private def decodeState(state: String, status: Option[String]): WorkerState = {
    state match {
      case "created" => WorkerState.Pending
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
    listWorkers(false, stopWorkerRequest.isolationSpace, stopWorkerRequest.idFilter).flatMap { containers =>
      val decoded: Vector[(StopWorkerResponseElement, ListContainerResponseRow)] = containers.flatMap { row =>
        decodeListWorkerResponse(row, stopWorkerRequest.nameFilter).map { d =>
          StopWorkerResponseElement(
            id = d.id,
            name = d.nodeName
          ) -> row
        }
      }

      logger.info(s"Going to stop ${decoded.size} containers")

      Future.sequence(decoded.map {
        case (_, row) =>
          dockerClient.killContainer(row.Id)
      }).map { _ =>
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