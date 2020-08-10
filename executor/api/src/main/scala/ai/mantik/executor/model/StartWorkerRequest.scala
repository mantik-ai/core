package ai.mantik.executor.model

import ai.mantik.executor.model.docker.{ Container, DockerLogin }
import akka.util.ByteString
import io.circe.generic.JsonCodec
import ByteStringCodec._
import io.circe.Json

/**
 * Request for starting a MNP worker
 *
 * @param isolationSpace isolation space where to start the worker
 * @param id user specified id to give to the worker
 * @param definition definition of the worker
 * @param keepRunning if true, keep the worker running
 * @param nameHint if given, try to give the worker a name similar to this given name
 * @param ingressName if given, make the worker accessible from the outside
 */
@JsonCodec
case class StartWorkerRequest(
    isolationSpace: String,
    id: String,
    definition: WorkerDefinition,
    keepRunning: Boolean = false,
    nameHint: Option[String] = None,
    ingressName: Option[String] = None
)

@JsonCodec
sealed trait WorkerDefinition {

}

/**
 * An MNP Worker
 * @param initializer initializing code for ready to use Nodes.
 */
case class MnpWorkerDefinition(
    container: Container,
    extraLogins: Seq[DockerLogin] = Nil,
    initializer: Option[ByteString] = None
) extends WorkerDefinition

/**
 * An MNP Pipeline
 * @param definition JSON definition of Pipeline Helper
 *                   (Must match Golang Definition)
 */
case class MnpPipelineDefinition(
    definition: Json
) extends WorkerDefinition

/**
 * Response for [[StartWorkerRequest]]
 *
 * @param nodeName name of the Node (usually container or service name)
 * @param externalUrl an URL under which the Node is reachable from the outside.
 */
@JsonCodec
case class StartWorkerResponse(
    nodeName: String,
    externalUrl: Option[String] = None
)