package ai.mantik.executor.model

import ai.mantik.executor.model.docker.DockerLogin
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
 * @param nodeService the service definition.
 * @param extraLogins extra logins for docker usage.
 */
@JsonCodec
case class DeployServiceRequest(
    serviceId: String,
    nameHint: Option[String] = None,
    isolationSpace: String,
    nodeService: ContainerService,
    extraLogins: Seq[DockerLogin] = Nil
)

/**
 * Response for [[DeployServiceRequest]].
 *
 * @param serviceName the (probably internal) name under which the service was deployed.
 * @param url URL under which the service was deployed. Internal only.
 */
@JsonCodec
case class DeployServiceResponse(
    serviceName: String,
    url: String
)

