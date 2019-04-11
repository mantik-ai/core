package ai.mantik.executor.model

import io.circe.{ Decoder, Encoder, ObjectEncoder }
import io.circe.generic.JsonCodec
import io.circe.generic.semiauto

/** References a Resource in the Graph. */
@JsonCodec
case class NodeResourceRef(
    node: String,
    resource: String
)

object NodeResourceRef {

  // Shortcuts for Default Resource Names:

  def source(node: String): NodeResourceRef =
    NodeResourceRef(node, ExecutorModelDefaults.SourceResource)

  def transformation(node: String): NodeResourceRef =
    NodeResourceRef(node, ExecutorModelDefaults.TransformationResource)

  def sink(node: String): NodeResourceRef =
    NodeResourceRef(node, ExecutorModelDefaults.SinkResource)

  implicit val ordering = Ordering.by { s: NodeResourceRef => (s.node, s.resource) }
}

/** A directed link in the resource graph. */
@JsonCodec
case class Link(
    from: NodeResourceRef,
    to: NodeResourceRef
)

object Link {
  /** Shortcut for creating links. */
  def apply(link: (NodeResourceRef, NodeResourceRef)): Link = {
    Link(link._1, link._2)
  }

  /** Shortcut for creating many links. */
  def links(links: (NodeResourceRef, NodeResourceRef)*): Seq[Link] = links.map { case (from, to) => Link(from, to) }
}

/**
 * Defines a Dataflow graph.
 * @param T The node data type.
 */
case class Graph[+T](
    nodes: Map[String, Node[T]],
    links: Seq[Link] = Nil
)

object Graph {
  implicit def graphEncoder[T: Encoder]: ObjectEncoder[Graph[T]] = semiauto.deriveEncoder[Graph[T]]
  implicit def graphDecoder[T: Decoder]: Decoder[Graph[T]] = semiauto.deriveDecoder[Graph[T]]
}