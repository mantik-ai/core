package ai.mantik.planner.graph

import ai.mantik.componently.utils.Renderable
import io.circe.generic.semiauto
import io.circe.{Decoder, Encoder, ObjectEncoder}

import scala.language.implicitConversions

/**
  * Defines a Dataflow graph.
  * @tparam T The node data type.
  */
case class Graph[+T](
    nodes: Map[String, Node[T]],
    links: Seq[Link] = Nil
) {

  /**
    * Resolves a node input port
    */
  def resolveInput(ref: NodePortRef): Option[(Node[T], NodePort)] = {
    for {
      node <- nodes.get(ref.node)
      port <- vecGet(node.inputs, ref.port)
    } yield (node, port)
  }

  /**
    * Resolve a node output port.
    */
  def resolveOutput(ref: NodePortRef): Option[(Node[T], NodePort)] = {
    for {
      node <- nodes.get(ref.node)
      port <- vecGet(node.outputs, ref.port)
    } yield (node, port)
  }

  private def vecGet[T](vector: Vector[T], id: Int): Option[T] = {
    if (vector.isDefinedAt(id)) {
      Some(vector(id))
    } else {
      None
    }
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

      val items = orderedNodes.map { case (key, value) =>
        val links = linksByNode.get(key).getOrElse(Nil)
        val linkTree = if (links.isEmpty) {
          Renderable.Leaf("No Links")
        } else {
          Renderable.SubTree(
            items = links.map { link =>
              Renderable.Leaf(s"${link.from.node}/${link.from.port} -> ${link.to.node}/${link.to.port}")
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
