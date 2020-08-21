package ai.mantik.planner.graph

import io.circe.generic.JsonCodec

/** A directed link in the resource graph. */
@JsonCodec
case class Link(
    from: NodePortRef,
    to: NodePortRef
)

object Link {
  /** Shortcut for creating links. */
  def apply(link: (NodePortRef, NodePortRef)): Link = {
    Link(link._1, link._2)
  }

  /** Shortcut for creating many links. */
  def links(links: (NodePortRef, NodePortRef)*): Seq[Link] = links.map { case (from, to) => Link(from, to) }
}
