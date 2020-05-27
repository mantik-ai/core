package ai.mantik.executor.model

import io.circe.generic.JsonCodec

/** Request for stopping workers. */
@JsonCodec
case class StopWorkerRequest(
    isolationSpace: String,
    nameFilter: Option[String] = None,
    idFilter: Option[String] = None
)

/** Response for stopping workers. */
@JsonCodec
case class StopWorkerResponse(
    removed: Seq[StopWorkerResponseElement]
)

/** Element for removing workers. */
@JsonCodec
case class StopWorkerResponseElement(
    id: String,
    name: String
)
