package ai.mantik.planner.graph

import ai.mantik.componently.utils.Renderable
import io.circe.generic.JsonCodec

/** References a port in the Graph. */
@JsonCodec
case class NodePortRef(
    node: String,
    port: Int
)

object NodePortRef {

  implicit val ordering = Ordering.by { s: NodePortRef => (s.node, s.port) }

  implicit val renderable = Renderable.makeRenderable[NodePortRef] { value =>
    Renderable.Leaf(value.node + "/" + value.port)
  }
}