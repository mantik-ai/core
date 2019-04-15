package ai.mantik.executor.model

import io.circe.generic.JsonCodec

/**
 * Request for publishing an external service.
 *
 * @param isolationSpace insolation space in which to publish
 * @param serviceName name of the service
 * @param port port (inside kubernetes)
 * @param externalName external name or IP-Address
 * @param externalPort external port
 */
@JsonCodec
case class PublishServiceRequest(
    isolationSpace: String,
    serviceName: String,
    port: Int,
    externalName: String,
    externalPort: Int
)

/**
 * Response for publishing an external service.
 * @param name name from the sense of kubernetes.
 */
@JsonCodec
case class PublishServiceResponse(
    name: String
)