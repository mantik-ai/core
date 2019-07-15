package ai.mantik.executor.model

import ai.mantik.executor.model.docker.DockerLogin
import io.circe.Json
import io.circe.generic.JsonCodec

/**
 * A Request for the deployment of a single service.
 *
 * After successful deployment the service will keep running
 * and is reachable by a URL.
 *
 * @param serviceId a user specified id.
 * @param nameHint a hint for the service name, must be a valid DNS Name.
 * @param isolationSpace isolation space.
 * @param service the service definition.
 * @param extraLogins extra logins for docker usage.
 * @param ingress if set, an ingress resource with the given name will be added.
 */
@JsonCodec
case class DeployServiceRequest(
    serviceId: String,
    nameHint: Option[String] = None,
    isolationSpace: String,
    service: DeployableService,
    extraLogins: Seq[DockerLogin] = Nil,
    ingress: Option[String] = None
)

/** Something which can be deployed via [[DeployServiceRequest]]. */
@JsonCodec
sealed trait DeployableService

case object DeployableService {
  /** Deploy a Container Service. */
  case class SingleService(nodeService: ContainerService) extends DeployableService

  /**
   * Deploy a Pipeline. The pipeline definition must match the JSON Definition.
   * Of the Pipeline controller docker service.
   */
  case class Pipeline(pipeline: Json, port: Int = ExecutorModelDefaults.Port) extends DeployableService

  import scala.language.implicitConversions
  /** Automatic implicit conversion from Container Services (compatibility reasons) */
  implicit def fromSingleService(singleService: ContainerService): DeployableService = SingleService(singleService)
}

/**
 * Response for [[DeployServiceRequest]].
 *
 * @param serviceName the (probably internal) name under which the service was deployed.
 * @param url URL under which the service was deployed. Internal only.
 * @param externalUrl external URL under which the service was deployed (if ingress is enabled)
 */
@JsonCodec
case class DeployServiceResponse(
    serviceName: String,
    url: String,
    externalUrl: Option[String] = None
)

