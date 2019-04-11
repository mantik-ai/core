package ai.mantik.executor.model

import io.circe.generic.JsonCodec

/**
 * Represents a traditional Job request.
 *
 * @param isolationSpace resembles different kubernetes namespaces for different jobs
 * @param graph the Job Graph
 * @param contentType MIME-ContentType, will be forwared to coordinator.
 */
@JsonCodec
case class Job(
    isolationSpace: String,
    graph: Graph[NodeService],
    contentType: Option[String] = None
)
