package ai.mantik.executor.model

import ai.mantik.executor.model.docker.DockerLogin
import io.circe.generic.JsonCodec

/**
 * Represents a traditional Job request.
 *
 * @param isolationSpace resembles different kubernetes namespaces for different jobs
 * @param graph the Job Graph
 * @param contentType MIME-ContentType, will be forwared to coordinator.
 * @param extraLogins extra logins for accessing Docker Images.
 */
@JsonCodec
case class Job(
    isolationSpace: String,
    graph: Graph[NodeService],
    contentType: Option[String] = None,
    extraLogins: Seq[DockerLogin] = Nil
)
