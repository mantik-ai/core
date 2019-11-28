package ai.mantik.executor.model

import ai.mantik.componently.utils.Renderable
import io.circe.{ Decoder, Encoder, ObjectEncoder }
import io.circe.generic.JsonCodec
import io.circe.generic.semiauto
import scala.language.implicitConversions

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

  implicit val renderable = Renderable.makeRenderable[NodeResourceRef] { value =>
    Renderable.Leaf(value.node + "/" + value.resource)
  }
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
 * @tparam T The node data type.
 */
case class Graph[+T](
    nodes: Map[String, Node[T]],
    links: Seq[Link] = Nil
) {

  /**
   * Resolves a node resource reference.
   * @return the node and the resource, None if not found.
   */
  def resolveReference(ref: NodeResourceRef): Option[(Node[T], NodeResource)] = {
    for {
      node <- nodes.get(ref.node)
      resource <- node.resources.get(ref.resource)
    } yield (node, resource)
  }
}

object Graph {
  implicit def graphEncoder[T: Encoder]: ObjectEncoder[Graph[T]] = semiauto.deriveEncoder[Graph[T]]
  implicit def graphDecoder[T: Decoder]: Decoder[Graph[T]] = semiauto.deriveDecoder[Graph[T]]

  def empty[T]: Graph[T] = Graph(Map.empty)

  implicit def renderable[T: Renderable]: Renderable[Graph[T]] = new Renderable[Graph[T]] {
    override def buildRenderTree(value: Graph[T]): Renderable.RenderTree = {
      val orderedNodes = value.nodes.toIndexedSeq.sortBy(_._1)
      val linksByNode = value.links.groupBy(_.from.node)

      val items = orderedNodes.map {
        case (key, value) =>

          val links = linksByNode.get(key).getOrElse(Nil)
          val linkTree = if (links.isEmpty) {
            Renderable.Leaf("No Links")
          } else {
            Renderable.SubTree(
              items = links.map { link =>
                Renderable.Leaf(s"${link.from.resource} -> ${link.to.node}/${link.to.resource}")
              }.toVector,
              prefix = "- ",
              title = Some("Links")
            )

          }

          Renderable.SubTree(
            title = Some(s"Node ${key}"),
            items = Vector(
              Renderable.buildRenderTree(value),
              linkTree
            ),
            prefix = "  "
          )
      }
      Renderable.SubTree(
        items,
        title = Some("Graph")
      )
    }
  }
}