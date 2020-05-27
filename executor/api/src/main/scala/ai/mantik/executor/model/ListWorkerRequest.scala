package ai.mantik.executor.model

import ai.mantik.executor.model.docker.Container
import io.circe.generic.JsonCodec

/** Request current workers. */
@JsonCodec
case class ListWorkerRequest(
    isolationSpace: String,
    nameFilter: Option[String] = None,
    idFilter: Option[String] = None
)

/** Response for [[ListWorkerRequest]] */
@JsonCodec
case class ListWorkerResponse(
    workers: Seq[ListWorkerResponseElement]
)

@JsonCodec
sealed trait WorkerState

object WorkerState {
  case object Pending extends WorkerState
  case object Running extends WorkerState
  case class Failed(status: Int) extends WorkerState
  case object Succeeded extends WorkerState
}

@JsonCodec
case class ListWorkerResponseElement(
    nodeName: String,
    id: String,
    container: Container,
    state: WorkerState
)