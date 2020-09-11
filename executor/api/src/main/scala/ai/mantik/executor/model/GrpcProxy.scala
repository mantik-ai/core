package ai.mantik.executor.model

import io.circe.generic.JsonCodec

/**
 * Defines an GrpcProxy needed to communicate with MNP Nodes
 * @param proxyUrl if set, a proxy is needed in order to talk to nodes.
 *                 If None, MNP Nodes can be talked to directly
 */
@JsonCodec
case class GrpcProxy(
    proxyUrl: Option[String] = None
)
