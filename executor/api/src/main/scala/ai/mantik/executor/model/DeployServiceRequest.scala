package ai.mantik.executor.model

import ai.mantik.executor.model.docker.DockerLogin
import io.circe.generic.JsonCodec

/**
 * A Request for the deployment of a single service.
 *
 * After successful deployment the service will keep running
 * and is reachable by a URL.
 *
 * @param serviceName a user specified id.
 * @param isolationSpace isolation space.
 * @param nodeService the service definition.
 * @param extraLogins extra logins for docker usage.
 */
@JsonCodec
case class DeployServiceRequest(
    serviceName: String,
    isolationSpace: String,
    nodeService: ContainerService,
    extraLogins: Seq[DockerLogin] = Nil
)

/**
 * Response for [[DeployServiceRequest]].
 *
 * @param serviceId internal service id uder which the service was deployed.
 * @param url URL under which the service was deployed. Internal only.
 */
@JsonCodec
case class DeployServiceResponse(
    serviceId: String,
    url: String
)

