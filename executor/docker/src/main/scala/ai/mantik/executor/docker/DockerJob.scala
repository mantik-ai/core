package ai.mantik.executor.docker

import ai.mantik.executor.docker.DockerJob._
import ai.mantik.executor.docker.api.PullPolicy
import ai.mantik.executor.docker.api.structures.{ CreateContainerRequest, CreateVolumeRequest }

/** A Job translated into something we can issue to docker. */
case class DockerJob(
    id: String,
    workers: Vector[ContainerDefinition],
    coordinator: ContainerDefinition,
    payloadProviders: Vector[ContainerDefinition]
)

object DockerJob {

  case class VolumeDefinition(
      request: CreateVolumeRequest
  )

  case class ContainerDefinition(
      name: String,
      mainPort: Option[Int],
      pullPolicy: PullPolicy,
      createRequest: CreateContainerRequest
  ) {
    /** Add some labels to the container definition. */
    def addLabels(labels: Map[String, String]): ContainerDefinition = {
      copy(
        createRequest = createRequest.copy(
          Labels = createRequest.Labels ++ labels
        )
      )
    }
  }
}