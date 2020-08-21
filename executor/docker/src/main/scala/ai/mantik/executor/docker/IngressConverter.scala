package ai.mantik.executor.docker

case class IngressConverter(
    config: DockerExecutorConfig,
    dockerHost: String,
    ingressName: String
) {

  /** Creates the container definition with activated ingress. */
  def containerDefinitionWithIngress(containerDefinition: ContainerDefinition): ContainerDefinition = {
    containerDefinition.addLabels(
      ingressLabels(containerDefinition.mainPort.getOrElse(0))
    )
  }

  /** Creates necessary Labels to make ingress working */
  private def ingressLabels(containerPort: Int): Map[String, String] = {
    config.ingress.labels.mapValues { labelValue =>
      interpolateIngressString(labelValue, containerPort)
    } + (DockerConstants.IngressLabelName -> ingressName)
  }

  /** Returns the ingress URL */
  def ingressUrl: String = {
    interpolateIngressString(config.ingress.remoteUrl, containerPort = 0)
  }

  private def interpolateIngressString(in: String, containerPort: Int): String = {
    in
      .replace("${name}", ingressName)
      .replace("${dockerHost}", dockerHost)
      .replace("${traefikPort}", config.ingress.traefikPort.toString)
      .replace("${port}", containerPort.toString)
  }

}
