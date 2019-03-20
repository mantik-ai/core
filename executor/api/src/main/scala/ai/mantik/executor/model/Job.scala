package ai.mantik.executor.model

import io.circe.generic.JsonCodec

/** Represents a traditional Job request. */
@JsonCodec
case class Job(
    isolationSpace: String,
    graph: Graph
)
