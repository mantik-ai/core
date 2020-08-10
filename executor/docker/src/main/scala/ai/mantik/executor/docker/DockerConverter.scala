package ai.mantik.executor.docker

import java.util.Base64

import ai.mantik.executor.common.PayloadProvider
import ai.mantik.executor.docker.DockerJob.ContainerDefinition
import ai.mantik.executor.docker.api.PullPolicy
import ai.mantik.executor.docker.api.structures.{ CreateContainerHostConfig, CreateContainerNetworkSpecificConfig, CreateContainerRequest }
import ai.mantik.executor.model.docker.Container
import ai.mantik.executor.model.{ ContainerService, DataProvider }
import akka.util.ByteString
import cats.data.State
import io.circe.Json

class DockerConverter(
    config: DockerExecutorConfig,
    defaultLabels: Map[String, String]
) {

  /** Generate a Container Definition for a Worker. */
  def generateWorkerContainer(nodeName: String, node: ContainerService): State[NameGenerator, ContainerDefinition] = {
    State { nameGenerator: NameGenerator =>
      val (nextState, name) = nameGenerator.nodeName(nodeName)
      val resolved = config.common.dockerConfig.resolveContainer(node.main)

      val needData = node.dataProvider.isDefined

      // We are using the volume from the payload provider
      // not the other way round, because the payload provider must ensure
      // proper permissions inside it.
      val volumesFrom = if (needData) {
        Vector(s"${name.payloadProviderName}:rw")
      } else {
        Vector.empty
      }

      val request = CreateContainerRequest(
        Image = resolved.image,
        Cmd = resolved.parameters.toVector,
        Labels = defaultLabels + (
          DockerConstants.RoleLabelName -> DockerConstants.WorkerRole,
          DockerConstants.PortLabel -> node.port.toString
        ),
        // Volumes = volumes,
        HostConfig = CreateContainerHostConfig(
          VolumesFrom = volumesFrom,
          // For write access to data directory
          GroupAdd = Vector(DockerConstants.WorkerPayloadGroup)
        )
      )
      nextState -> ContainerDefinition(
        name.containerName,
        mainPort = Some(node.port),
        pullPolicy = pullPolicy(resolved),
        request
      )
    }
  }

  /**
   * Generate the definition for a payload provider.
   * @return Payload provider or Nom
   */
  def generatePayloadProvider(name: String, dataProvider: DataProvider): State[NameGenerator, ContainerDefinition] = {
    NameGenerator.stateChange(_.nodeName(name)) { nodeName =>

      val extraArguments = PayloadProvider.createExtraArguments(dataProvider)
      val allArguments = config.common.payloadPreparer.parameters.toVector ++
        extraArguments

      ContainerDefinition(
        name = nodeName.payloadProviderName,
        mainPort = None,
        pullPolicy = pullPolicy(config.common.payloadPreparer),
        createRequest = CreateContainerRequest(
          Image = config.common.payloadPreparer.image,
          Cmd = allArguments,
          Labels = defaultLabels + (DockerConstants.RoleLabelName -> DockerConstants.PayloadProviderRole),
          Env = Vector(),
          HostConfig = CreateContainerHostConfig(
            // VolumesFrom = Vector(volumesFrom),
            // For write access to data directory
            GroupAdd = Vector(DockerConstants.WorkerPayloadGroup)
          )
        )
      )
    }
  }

  /** Generate a pull policy for a container. */
  def pullPolicy(container: Container): PullPolicy = {
    // Should be similar to kubernetes
    if (config.common.disablePull) {
      PullPolicy.Never
    } else {
      container.imageTag match {
        case None           => PullPolicy.Always
        case Some("latest") => PullPolicy.Always
        case Some(other)    => PullPolicy.IfNotPresent
      }
    }
  }

  def generateNewWorkerContainer(
    containerName: String,
    id: String,
    container: Container,
    workerNetworkId: Option[String]
  ): ContainerDefinition = {
    val resolved = config.common.dockerConfig.resolveContainer(container)

    val request = CreateContainerRequest(
      Image = resolved.image,
      Cmd = resolved.parameters.toVector,
      Labels = defaultLabels + (
        DockerConstants.RoleLabelName -> DockerConstants.WorkerRole,
        DockerConstants.TypeLabelName -> DockerConstants.WorkerType,
        DockerConstants.IdLabelName -> id
      )
    )

    val requestWithNetwork = workerNetworkId.map { networkId =>
      request.withNetworkId(config.workerNetwork, networkId)
    }.getOrElse(
      request
    )

    ContainerDefinition(
      containerName,
      mainPort = Some(8502),
      pullPolicy = pullPolicy(resolved),
      requestWithNetwork
    )
  }

  /** Generate an MNP preparer for a node. */
  def generateMnpPreparer(
    mainContainerName: String,
    initRequest: ByteString,
    id: String,
    workerNetworkId: Option[String]
  ): ContainerDefinition = {
    val preparerName = mainContainerName + "_init"
    val mainAddress = s"${mainContainerName}:8502"
    val parameters = Seq(
      "--address", mainAddress
    )
    val allParameters = config.common.mnpPreparer.parameters ++ parameters
    val encodedInitRequest = Base64.getEncoder.encodeToString(initRequest.toArray[Byte])

    val envValue = s"MNP_INIT=${encodedInitRequest}"

    val request = CreateContainerRequest(
      Image = config.common.mnpPreparer.image,
      Cmd = allParameters.toVector,
      Labels = defaultLabels + (
        DockerConstants.RoleLabelName -> DockerConstants.PayloadProviderRole,
        DockerConstants.TypeLabelName -> DockerConstants.WorkerType,
        DockerConstants.IdLabelName -> id
      ),
      Env = Vector(envValue)
    )

    val requestWithNetwork = workerNetworkId.map { networkId =>
      request.withNetworkId(config.workerNetwork, networkId)
    }.getOrElse(
      request
    )

    ContainerDefinition(
      preparerName,
      None,
      pullPolicy = pullPolicy(config.common.mnpPreparer),
      requestWithNetwork
    )
  }

  def generateNewPipelineContainer(
    containerName: String,
    id: String,
    pipelineDefinition: Json,
    workerNetworkId: Option[String]
  ): ContainerDefinition = {
    val container = config.common.mnpPipelineController
    val pipelineEnv = "PIPELINE=" + pipelineDefinition.noSpaces
    val extraArgs = Vector("-port", "8502")
    val allParameters = container.parameters ++ extraArgs
    val request = CreateContainerRequest(
      Image = container.image,
      Cmd = allParameters.toVector,
      Labels = defaultLabels + (
        DockerConstants.RoleLabelName -> DockerConstants.PipelineRole,
        DockerConstants.TypeLabelName -> DockerConstants.WorkerType,
        DockerConstants.IdLabelName -> id
      ),
      Env = Vector(pipelineEnv)
    )

    val requestWithNetwork = workerNetworkId.map { networkId =>
      request.withNetworkId(config.workerNetwork, networkId)
    }.getOrElse(
      request
    )

    ContainerDefinition(
      containerName,
      Some(8502),
      pullPolicy = pullPolicy(config.common.mnpPreparer),
      requestWithNetwork
    )
  }
}
