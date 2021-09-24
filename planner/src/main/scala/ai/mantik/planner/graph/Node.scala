/*
 * This file is part of the Mantik Project.
 * Copyright (c) 2020-2021 Mantik UG (Haftungsbeschränkt)
 * Authors: See AUTHORS file
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.
 *
 * Additionally, the following linking exception is granted:
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with other code, such other code is not for that reason
 * alone subject to any of the requirements of the GNU Affero GPL
 * version 3.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license.
 */
package ai.mantik.planner.graph

import ai.mantik.componently.utils.Renderable
import io.circe.generic.{JsonCodec, semiauto}
import io.circe.{Decoder, Encoder, ObjectEncoder}

/**
  * Describes a Node in a [[Graph]]
  *
  * @param service Describes what is needed to access resources on this node.
  * @param inputs input ports
  * @param outputs output ports
  *
  * @tparam T The node Service type
  */
case class Node[+T](
    service: T,
    inputs: Vector[NodePort] = Vector.empty,
    outputs: Vector[NodePort] = Vector.empty
)

/** A Node Resource. */
@JsonCodec
case class NodePort(
    contentType: String
)

object Node {

  implicit def encoder[T: Encoder]: Encoder.AsObject[Node[T]] = semiauto.deriveEncoder[Node[T]]
  implicit def decoder[T: Decoder]: Decoder[Node[T]] = semiauto.deriveDecoder[Node[T]]

  /** Generates a Default Sink with one input port */
  def sink[T](service: T, contentType: String): Node[T] = Node[T](
    service,
    inputs = Vector(NodePort(contentType)),
    outputs = Vector.empty
  )

  /** Generates a Default Source with one output port */
  def source[T](service: T, contentType: String): Node[T] = Node[T](
    service,
    outputs = Vector(NodePort(contentType)),
    inputs = Vector.empty
  )

  /** Generates a node with a single input and output of the same content type. */
  def transformer[T](service: T, contentType: String): Node[T] = Node[T](
    service,
    inputs = Vector(NodePort(contentType)),
    outputs = Vector(NodePort(contentType))
  )

  implicit def renderable[T](implicit renderable: Renderable[T]): Renderable[Node[T]] = new Renderable[Node[T]] {
    override def buildRenderTree(value: Node[T]): Renderable.RenderTree = {
      def renderNodePort(nodePort: NodePort): Renderable.RenderTree = {
        Renderable.Leaf(s"Port (${nodePort.contentType})")
      }
      def renderNodePorts(nodePort: Vector[NodePort]): Renderable.RenderTree = {
        Renderable.SubTree(nodePort.map(renderNodePort))
      }
      Renderable.keyValueList(
        "Node",
        "service" -> Renderable.buildRenderTree(value.service),
        "inputs" -> renderNodePorts(value.inputs),
        "outputs" -> renderNodePorts(value.outputs)
      )
    }
  }
}
