package ai.mantik.executor.kubernetes

import io.circe.{ Decoder, Encoder }

/** The plan for the coordinator process. Must match the Definition in the Go Code. */
case class CoordinatorPlan(
    nodes: Map[String, CoordinatorPlan.Node],
    flows: Seq[Seq[CoordinatorPlan.NodeResourceRef]]
)

object CoordinatorPlan {
  case class Node(
      address: Option[String] = None,
      url: Option[String] = None
  ) {
    require(address.isDefined != url.isDefined, "Either Adress or URL must be defined, but not both")
  }

  case class NodeResourceRef(
      node: String,
      resource: String,
      contentType: Option[String]
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
