package ai.mantik.executor.docker

import ai.mantik.componently.AkkaRuntime
import ai.mantik.executor.Errors
import ai.mantik.executor.docker.DockerJob.ContainerDefinition
import ai.mantik.executor.docker.DockerStateManager.LogContent
import ai.mantik.executor.docker.api.structures.{ CreateContainerResponse, ListContainerRequestFilter }
import ai.mantik.executor.docker.api.{ DockerClient, DockerOperations }
import cats.implicits._
import com.typesafe.scalalogging.Logger

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }

/** Responsible for running a single job. */
class DockerProcessRunner(
    dockerClient: DockerClient,
    stateManager: DockerStateManager,
    dockerOperations: DockerOperations
)(implicit akkaRuntime: AkkaRuntime) {
  import ai.mantik.componently.AkkaHelper._
  val logger = Logger(getClass)

  // TODO: Configurable!
  val PayloadTimeout = 1.minute
  val JobTimeout = 24.hours

  /** Run the job. Execution is done asynchronously. */
  def runJob(job: DockerJob): Future[Unit] = {
    val mainResult = for {
      _ <- createContainers(job)
      _ = stateManager.setState(job.id, DockerStateManager.JobCreated())
      _ <- runPayloadProviders(job)
      _ = stateManager.setState(job.id, DockerStateManager.ContainersInitialized())
      _ <- runWorkersAndCoordinator(job)
      _ = stateManager.setState(job.id, DockerStateManager.JobRunning())
      _ <- waitCoordinator(job)
    } yield {
      ()
    }

    mainResult.transformWith {
      case Success(_) =>
        for {
          log <- tryGetLogs(job)
          _ = stateManager.setState(job.id, DockerStateManager.JobSucceeded(LogContent(log)))
          _ <- cleanup(job)
        } yield {
          ()
        }
      case Failure(error) =>
        logger.warn(s"Job ${job.id} failed", error)
        val basicInformation = Option(error.getMessage).getOrElse("unknown error")
        // try to get logs

        for {
          logs <- tryGetLogs(job)
          _ = stateManager.setState(
            job.id,
            DockerStateManager.JobFailed(basicInformation, LogContent(logs))
          )
          _ <- cleanup(job)
        } yield {
          ()
        }
    }
  }

  /** Assemble a service. */
  def assembleService(service: DockerService): Future[Unit] = {
    val mainResult = for {
      worker <- createContainer(service.worker)
      _ <- service.payloadProvider.map(createContainer).sequence
      _ = stateManager.setState(service.id, DockerStateManager.ServiceCreated())
      _ <- service.payloadProvider.map(runPayloadProviderAndWait).sequence
      _ = stateManager.setState(service.id, DockerStateManager.ServiceInitialized())
      _ <- dockerClient.startContainer(worker.Id)
      _ <- service.payloadProvider.map { p => removeContainerIgnoreResult(p.name) }.sequence
      _ = stateManager.setState(service.id, DockerStateManager.ServiceInstalled(
        userServiceId = service.userServiceId,
        internalUrl = service.internalUrl
      ))
    } yield {
      // ok, done
      ()
    }

    mainResult.transformWith {
      case Success(_) =>
        Future.successful(Success(()))
      case Failure(e) =>
        for {
          _ <- removeContainerIgnoreResult(service.worker.name)
          _ <- service.payloadProvider.map(p => removeContainerIgnoreResult(p.name)).sequence
          _ = stateManager.setState(service.id, DockerStateManager.ServiceFailed(e.getMessage))
        } yield {
          Failure(e)
        }
    }
  }

  /** Remove a service. */
  def removeService(id: String): Future[Unit] = {
    for {
      containers <- dockerClient.listContainersFiltered(
        true,
        ListContainerRequestFilter.forLabelKeyValue(
          DockerConstants.ManagedByLabelName -> DockerConstants.ManabedByLabelValue,
          DockerConstants.TypeLabelName -> DockerConstants.ServiceType,
          DockerConstants.IdLabelName -> id
        )
      )
      _ <- containers.map { containerElement =>
        removeContainerIgnoreResult(containerElement.Id)
      }.sequence
      _ = stateManager.remove(id)
    } yield { () }
  }

  private def tryGetLogs(job: DockerJob): Future[Option[String]] = {
    dockerClient.containerLogs(job.coordinator.name, true, true).transform { result: Try[String] => Try(result.toOption) }
  }

  private def createContainers(job: DockerJob): Future[Unit] = {
    for {
      _ <- Future.sequence(job.payloadProviders.map { payloadProvider =>
        createContainer(payloadProvider)
      })
      _ <- Future.sequence(job.workers.map { container =>
        createContainer(container)
      })
      _ <- createContainer(job.coordinator)
    } yield {
      ()
    }
  }

  private def createContainer(containerDefinition: ContainerDefinition): Future[CreateContainerResponse] = {
    for {
      _ <- dockerOperations.executePullPolicy(containerDefinition.pullPolicy, containerDefinition.createRequest.Image)
      response <- dockerClient.createContainer(containerDefinition.name, containerDefinition.createRequest)
    } yield response
  }

  private def runPayloadProviders(job: DockerJob): Future[Unit] = {
    Future.sequence(job.payloadProviders.map { payloadProvider =>
      runPayloadProviderAndWait(payloadProvider)
    }).map { _ =>
      ()
    }
  }

  private def runPayloadProviderAndWait(payloadProvider: ContainerDefinition): Future[Unit] = {
    for {
      _ <- dockerClient.startContainer(payloadProvider.name)
      result <- dockerOperations.waitContainer(payloadProvider.name, PayloadTimeout)
    } yield {
      if (result.StatusCode != 0) {
        throw new Errors.CouldNotExecutePayload(s"Payload initializers returned !=0 of ${result.StatusCode}")
      }
    }
  }

  private def runWorkersAndCoordinator(job: DockerJob): Future[Unit] = {
    for {
      _ <- Future.sequence(job.workers.map { worker =>
        dockerClient.startContainer(worker.name)
      })
      _ <- dockerClient.startContainer(job.coordinator.name)
    } yield {
      ()
    }
  }

  private def waitCoordinator(job: DockerJob): Future[Unit] = {
    for {
      result <- dockerOperations.waitContainer(job.coordinator.name, JobTimeout)
    } yield {
      if (result.StatusCode != 0) {
        throw new Errors.CouldNotExecutePayload(s"Coordinator failed with code != 0 of ${result.StatusCode}")
      }
    }
  }

  private def cleanup(job: DockerJob): Future[Unit] = {
    val allContainers = (job.coordinator +: job.payloadProviders) ++ job.workers
    Future.sequence(allContainers.map { container =>
      removeContainerIgnoreResult(container.name)
    }).map(_ => ())
  }

  private def removeContainerIgnoreResult(name: String): Future[Unit] = {
    dockerClient.removeContainer(name, true).transform {
      case Failure(e) =>
        // This can happen, e.g. on initialization errors.
        logger.info(s"Removing container ${name} failed (ignoring)", e)
        Success(())
      case Success(_) =>
        Success(())
    }
  }
}
