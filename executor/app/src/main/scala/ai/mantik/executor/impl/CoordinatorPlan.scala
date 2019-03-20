package ai.mantik.executor.impl

import io.circe.{ Decoder, Encoder }
import ai.mantik.executor.model.NodeResourceRef

/** The plan for the coordinator process. Must match the Definition in the Go Code. */
case class CoordinatorPlan(
    nodes: Map[String, CoordinatorPlan.Node],
    flows: Seq[Seq[NodeResourceRef]],
    contentType: Option[String] = None
)

object CoordinatorPlan {
  case class Node(
      address: String
  )

  // JSON Encoder
  import io.circe.generic.semiauto._

  implicit val nodeEncoder: Encoder[Node] = deriveEncoder[Node]
  implicit val nodeDecoder: Decoder[Node] = deriveDecoder[Node]
  implicit val nodeResourceRefEncoder: Encoder[NodeResourceRef] = deriveEncoder[NodeResourceRef]
  implicit val nodeResourceRefDecoder: Decoder[NodeResourceRef] = deriveDecoder[NodeResourceRef]
  implicit val encoder: Encoder[CoordinatorPlan] = deriveEncoder[CoordinatorPlan]
  implicit val decoder: Decoder[CoordinatorPlan] = deriveDecoder[CoordinatorPlan]
}
