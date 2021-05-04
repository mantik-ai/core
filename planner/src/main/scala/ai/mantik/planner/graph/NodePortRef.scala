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
