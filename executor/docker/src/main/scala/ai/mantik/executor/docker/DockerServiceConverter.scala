package ai.mantik.executor.docker

import ai.mantik.executor.docker.DockerJob.ContainerDefinition
import ai.mantik.executor.docker.api.structures.CreateContainerRequest
import ai.mantik.executor.model.{ DeployServiceRequest, DeployableService }
import cats.data.State

/** Converts ServiceRequests into something we can add to Docker. */
class DockerServiceConverter(
    config: DockerExecutorConfig,
    internalId: String,
    serviceRequest: DeployServiceRequest,
    dockerHost: String
) {

  private val MainNodeName = "node"

  val DefaultLabels = Map(
    DockerConstants.IsolationSpaceLabelName -> serviceRequest.isolationSpace,
    DockerConstants.IdLabelName -> internalId,
    DockerConstants.UserIdLabelName -> serviceRequest.serviceId,
    DockerConstants.ManagedByLabelName -> DockerConstants.ManagedByLabelValue,
    DockerConstants.TypeLabelName -> DockerConstants.ServiceType
  )

  val dockerConverter = new DockerConverter(config, DefaultLabels)

  def converted: DockerService = {
    val service = for {
      c <- mainContainer
      p <- payloadProvider
    } yield {
      DockerService(
        id = internalId,
        userServiceId = serviceRequest.serviceId,
        externalUrl = ingressUrl,
        worker = c,
        payloadProvider = p
      )
    }
    service.run(NameGenerator(
      internalId
    )).value._2
  }

  private def ingressLabels(containerPort: Int): Map[String, String] = serviceRequest.ingress match {
    case Some(_) =>
      config.ingress.labels.mapValues { labelValue =>
        interpolateIngressString(labelValue, containerPort)
      }
    case None => Map.empty
  }

  private def ingressUrl: Option[String] = serviceRequest.ingress.map { _ =>
    interpolateIngressString(config.ingress.remoteUrl, containerPort = 0)
  }

  private def interpolateIngressString(in: String, containerPort: Int): String = {
    val ingressName = serviceRequest.ingress.getOrElse("")
    in
      .replace("${name}", ingressName)
      .replace("${dockerHost}", dockerHost)
      .replace("${traefikPort}", config.ingress.traefikPort.toString)
      .replace("${port}", containerPort.toString)
  }

  private val mainContainer: State[NameGenerator, ContainerDefinition] = {
    serviceRequest.service match {
      case singleService: DeployableService.SingleService =>
        dockerConverter
          .generateWorkerContainer(MainNodeName, singleService.nodeService)
          .map(_.addLabels(ingressLabels(singleService.nodeService.port)))
      case pipeline: DeployableService.Pipeline =>
        val container = config.common.pipelineController
        val pipelineEnv = "PIPELINE=" + pipeline.pipeline.noSpaces
        val extraArgs = Vector("-port", pipeline.port.toString)
        NameGenerator.stateChange(_.nodeName(MainNodeName)) { nodeName =>
          ContainerDefinition(
            name = nodeName.containerName,
            mainPort = Some(pipeline.port),
            pullPolicy = dockerConverter.pullPolicy(container),
            createRequest = CreateContainerRequest(
              Image = container.image,
              Cmd = container.parameters.toVector ++ extraArgs,
              Labels = DefaultLabels + (
                DockerConstants.RoleLabelName -> DockerConstants.PipelineRole,
                DockerConstants.PortLabel -> pipeline.port.toString
              ) ++ ingressLabels(pipeline.port),
              Env = Vector(pipelineEnv)
            )
          )
        }
    }
  }

  private val payloadProvider: State[NameGenerator, Option[ContainerDefinition]] = {
    serviceRequest.service match {
      case singleService: DeployableService.SingleService =>
        singleService.nodeService.dataProvider match {
          case Some(dataProvider) => dockerConverter.generatePayloadProvider(MainNodeName, dataProvider).map(Some(_))
          case None               => State.pure(None)
        }
      case _: DeployableService.Pipeline =>
        State.pure(None)
    }
  }

}
