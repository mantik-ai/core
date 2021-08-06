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
  logger.info(s"Default Repo: ${executorConfig.common.dockerConfig.defaultImageRepository.getOrElse("<empty>")}")
  logger.info(s"Default Tag:  ${executorConfig.common.dockerConfig.defaultImageTag.getOrElse("<empty>")}")
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

  override def grpcProxy(): Future[GrpcProxy] = {
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
          logger.info(s"gRpc Proxy not enabled")
          GrpcProxy(None)
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
    listContainers(true, listWorkerRequest.nameFilter, listWorkerRequest.idFilter)
      .map { response =>
        val workers = response.flatMap(decodeListWorkerResponse)
        ListWorkerResponse(
          workers
        )
      }
  }

  /** List our containers within Docker
    *
    * @param all also include stopped ones
    * @param nameFilter if set, look for specific named containers
    * @param userIdFilter if set, look for specific user id
    * @param onlyWorkers if true, only Mnp Workers are returned.
    */
  private def listContainers(
      all: Boolean,
      nameFilter: Option[String],
      userIdFilter: Option[String],
      onlyWorkers: Boolean = true
  ): Future[Vector[ListContainerResponseRow]] = {
    val labelFilters = Seq(
      DockerConstants.IsolationSpaceLabelName -> Some(executorConfig.common.isolationSpace),
      LabelConstants.ManagedByLabelName -> Some(LabelConstants.ManagedByLabelValue),
      LabelConstants.UserIdLabelName -> userIdFilter,
      LabelConstants.RoleLabelName -> (if (onlyWorkers) Some(LabelConstants.role.worker) else None)
    ).collect { case (key, Some(value)) =>
      key -> value
    }

    dockerOperations
      .listContainers(
        all,
        labelFilters
      )
      .map { rawResponse =>
        nameFilter match {
          case Some(name) =>
            rawResponse.filter { r =>
              r.Names.headOption.exists { s =>
                s.stripPrefix("/").startsWith(name)
              }
            }
          case None =>
            rawResponse
        }
      }
  }

  private def decodeListWorkerResponse(
      listContainerResponseRow: ListContainerResponseRow
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
    listContainers(
      all,
      stopWorkerRequest.nameFilter,
      stopWorkerRequest.idFilter,
      onlyWorkers = false
    ).flatMap { containers =>
      val onlyWorkers =
        containers.filter(_.Labels.get(LabelConstants.RoleLabelName).contains(LabelConstants.role.worker))

      // Note: containers may contain containers of other roles, we have to find the correct ones using onlyWorkers
      val workerIds: Set[String] = (onlyWorkers.flatMap { worker =>
        worker.Labels.get(LabelConstants.InternalIdLabelName)
      }).toSet

      val toDelete = containers.filter { container =>
        container.Labels.get(LabelConstants.InternalIdLabelName).exists(id => workerIds.contains(id))
      }

      val result = onlyWorkers.flatMap { row =>
        decodeListWorkerResponse(row).map { r =>
          StopWorkerResponseElement(
            id = r.id,
            name = r.nodeName
          )
        }
      }

      logger.info(s"Going to stop ${toDelete.size} containers associated to ${result.size} workers")

      Future
        .sequence(toDelete.map { row =>
          if (stopWorkerRequest.remove.value) {
            // no need to kill before, we are forcing removal
            dockerOperations.removeContainer(row.Id)
          } else {
            if (decodeState(row) == WorkerState.Running) {
              dockerOperations.killContainer(row.Id)
            } else {
              Future.successful(())
            }
          }
        })
        .map { _ =>
          StopWorkerResponse(
            result
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
