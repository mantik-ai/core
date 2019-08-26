package ai.mantik.executor.docker
import ai.mantik.componently.{ AkkaRuntime, ComponentBase }
import ai.mantik.executor.docker.api.{ DockerClient, DockerOperations }
import ai.mantik.executor.docker.api.structures.ListContainerRequestFilter
import ai.mantik.executor.docker.buildinfo.BuildInfo
import ai.mantik.executor.model._
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

  // ENsuring traefik as soon as we have the state loaded (which means docker connectivity)
  stateManagerFuture.foreach(_ => ensureTraefikIfEnabled())

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
    for {
      stateManager <- stateManagerFuture
      containers <- dockerClient.listContainers(true)
    } yield {
      val usedNames = containers.flatMap(_.Names)
      generateName(usedNames, info, stateManager)
    }
  }

  private def generateName(usedNames: Seq[String], info: DockerStateManager.Info, stateManager: DockerStateManager): String = {
    val maxLength = 5
    var i = 1
    while (i < maxLength) {
      val candidate = NameGenerator.generateRootName(i)
      if (!usedNames.exists { usedName =>
        usedName.startsWith(candidate) && usedName.startsWith("/" + candidate)
      } && stateManager.reserve(candidate, info)) {
        return candidate
      }
      i += 1
    }
    throw new IllegalStateException("Could not generate a name")
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
        DockerConstants.ManagedByLabelName -> DockerConstants.ManabedByLabelValue,
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

  private def ensureTraefikIfEnabled(): Unit = {
    new TraefikInitializer(dockerClient, executorConfig, dockerOperations).ensureTraefikIfEnabled()
  }
}

class DockerExecutorProvider @Inject() (implicit akkaRuntime: AkkaRuntime) extends Provider[DockerExecutor] {
  override def get(): DockerExecutor = {
    val dockerExecutorConfig = DockerExecutorConfig.fromTypesafeConfig(akkaRuntime.config)
    val dockerClient = new DockerClient()
    new DockerExecutor(dockerClient, dockerExecutorConfig)
  }
}