package ai.mantik.executor.model

import io.circe.generic.JsonCodec

/**
  * Request for stopping workers.
  * @param isolationSpace the isolation space in which to stop workers
  * @param nameFilter if set, only remove workers of a given node name
  * @param idFilter if set, only remove workers of a given user id
  * @param remove if true, remove the workers completely
  */
@JsonCodec
case class StopWorkerRequest(
    isolationSpace: String,
    nameFilter: Option[String] = None,
    idFilter: Option[String] = None,
    remove: StringWrapped[Boolean] = true
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
