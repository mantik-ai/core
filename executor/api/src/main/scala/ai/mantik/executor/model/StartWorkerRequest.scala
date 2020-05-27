package ai.mantik.executor.model

import ai.mantik.executor.model.docker.{ Container, DockerLogin }
import io.circe.generic.JsonCodec

/** Request for starting a MNP worker */
@JsonCodec
case class StartWorkerRequest(
    isolationSpace: String,
    id: String,
    container: Container,
    extraLogins: Seq[DockerLogin] = Nil
)

/** Response for [[StartWorkerRequest]] */
@JsonCodec
case class StartWorkerResponse(
    nodeName: String
)