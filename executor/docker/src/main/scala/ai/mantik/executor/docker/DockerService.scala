package ai.mantik.executor.docker

import ai.mantik.executor.docker.DockerJob.ContainerDefinition
import ai.mantik.executor.model.ExecutorModelDefaults

/**
 * A Converted service.
 * @param id internal id of the service
 * @param userServiceId user provided id
 * @param externalUrl external ingress URL
 * @param worker the worker container
 * @param payloadProvider optional payload container.
 */
case class DockerService(
    id: String,
    userServiceId: String,
    externalUrl: Option[String],
    worker: ContainerDefinition,
    payloadProvider: Option[ContainerDefinition]
) {
  def internalUrl: String = DockerService.formatInternalUrl(worker.name, worker.mainPort.getOrElse(ExecutorModelDefaults.Port))
}

object DockerService {

  /** Format the fake internal address, used to reference DockerServices. */
  def formatInternalAddress(containerName: String, port: Int): String = {
    containerName + DockerConstants.InternalServiceNameSuffix + ":" + port
  }

  /** Format the internal url from container name and port. */
  def formatInternalUrl(containerName: String, port: Int): String = {
    val internalAddress = formatInternalAddress(containerName, port)
    "http://" + internalAddress
  }

  /**
   * Check if a URL is referencing an internal service, if yes it returns
   * the name of the container and the internal address.
   */
  def detectInternalService(urlOrAddress: String): Option[(String, String)] = {
    val withoutHttp = if (urlOrAddress.startsWith("http://")) {
      urlOrAddress.stripPrefix("http://")
    } else {
      urlOrAddress
    }
    val idx = withoutHttp.indexOf(DockerConstants.InternalServiceNameSuffix + ":")
    val idx2 = withoutHttp.indexOf("/")
    if (idx > 0 && (idx2 < 0 || idx2 > idx)) {
      val containerName = withoutHttp.substring(0, idx)
      val internalServiceName = withoutHttp.substring(0, idx + DockerConstants.InternalServiceNameSuffix.length)
      Some(containerName -> internalServiceName)
    } else {
      None
    }
  }
}