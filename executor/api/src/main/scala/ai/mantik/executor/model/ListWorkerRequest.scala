package ai.mantik.executor.model

import ai.mantik.executor.model.docker.Container
import io.circe.generic.JsonCodec

/**
  * Request current workers.
  */
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
sealed trait WorkerState {
  def isTerminal: Boolean
}

object WorkerState {
  case object Pending extends WorkerState {
    override def isTerminal: Boolean = false
  }
  case object Running extends WorkerState {
    override def isTerminal: Boolean = false
  }
  case class Failed(status: Int, error: Option[String] = None) extends WorkerState {
    override def isTerminal: Boolean = true
  }
  case object Succeeded extends WorkerState {
    override def isTerminal: Boolean = true
  }
}

@JsonCodec
sealed trait WorkerType

object WorkerType {
  case object MnpWorker extends WorkerType
  case object MnpPipeline extends WorkerType
}

@JsonCodec
case class ListWorkerResponseElement(
    nodeName: String,
    id: String,
    container: Option[Container],
    state: WorkerState,
    `type`: WorkerType,
    externalUrl: Option[String]
)
