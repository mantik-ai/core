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
