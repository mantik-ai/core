package ai.mantik.executor.docker

import ai.mantik.executor.common.PayloadProvider
import ai.mantik.executor.docker.DockerJob.ContainerDefinition
import ai.mantik.executor.docker.api.PullPolicy
import ai.mantik.executor.docker.api.structures.{ CreateContainerHostConfig, CreateContainerRequest }
import ai.mantik.executor.model.docker.Container
import ai.mantik.executor.model.{ ContainerService, DataProvider }
import cats.data.State

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
}
