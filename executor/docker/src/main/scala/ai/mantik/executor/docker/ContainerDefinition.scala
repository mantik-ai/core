package ai.mantik.executor.docker

import ai.mantik.executor.docker.api.PullPolicy
import ai.mantik.executor.docker.api.structures.{CreateContainerRequest, CreateVolumeRequest}

/** A definition how to create a new container */
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
