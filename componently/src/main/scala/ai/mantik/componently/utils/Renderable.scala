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
package ai.mantik.componently.utils
import io.circe.Json

import scala.language.implicitConversions

/**
  * Type class for things which can render themself into Strings (with Newlines)
  * Mainly used for debugging complex graph structures, more readable than JSON.
  */
trait Renderable[T] {

  /** Render something into a RenderTree. */
  def buildRenderTree(value: T): Renderable.RenderTree
}

object Renderable {

  /** Describes the way something is rendered. */
  sealed trait RenderTree {

    def renderAsString: String = {
      val builder = new StringBuilder
      render(builder, 0)
      builder.result()
    }

    private[utils] def render(builder: StringBuilder, depth: Int)

  }

  /** A leaf, contains a string without newlines. */
  case class Leaf(s: String) extends RenderTree {
    override private[utils] def render(builder: StringBuilder, depth: Int): Unit = {
      builder ++= s
    }
  }

  /** A Subtree with elements. */
  case class SubTree(
      items: IndexedSeq[RenderTree],
      prefix: String = "- ",
      title: Option[String] = None
  ) extends RenderTree {
    override private[utils] def render(builder: StringBuilder, depth: Int): Unit = {
      if (items.isEmpty) {
        title match {
          case Some(defined) => builder ++= defined ++ ": <empty>"
          case None          => builder ++= "<empty>"
        }
      } else {
        title match {
          case Some(defined) =>
            builder ++= defined
            builder ++= "\n"
            builder ++= " " * depth
          case None => // nothing
        }
        val newLength = depth + prefix.length
        builder ++= prefix
        items.head.render(builder, newLength)
        items.tail.foreach { item =>
          builder ++= "\n"
          builder ++= " " * depth + prefix
          item.render(builder, newLength)
        }
      }
    }
  }

  /** Helper to get key-value lists working. */
  case class RenderableWrapper(tree: RenderTree)
  implicit def makeRenderableWrapper[T: Renderable](x: T): RenderableWrapper = RenderableWrapper(buildRenderTree(x))

  /** Build a Tree for Key value lists */
  def keyValueList(
      title: String,
      values: (String, RenderableWrapper)*
  ): RenderTree = {
    if (values.isEmpty) {
      Leaf(title)
    } else {
      SubTree(
        title = Some(title),
        items = values.map { case (key, value) =>
          value.tree match {
            case Leaf(leaf) => Leaf(key + ": " + leaf)
            case s: SubTree => {
              s.title match {
                case None        => s.copy(title = Some(key))
                case Some(title) => s.copy(title = Some(key + ": " + title))
              }
            }
          }
        }.toIndexedSeq
      )
    }
  }

  /** Render something renderable. */
  def renderAsString[T: Renderable](value: T): String = {
    buildRenderTree(value).renderAsString
  }

  def buildRenderTree[T](value: T)(implicit renderer: Renderable[T]): RenderTree = {
    renderer.buildRenderTree(value)
  }

  /** Helper for making renderable instances. */
  def makeRenderable[T](f: T => RenderTree): Renderable[T] = new Renderable[T] {
    override def buildRenderTree(value: T): RenderTree = f(value)
  }

  // Default Renderers
  implicit val renderString = makeRenderable[String](Leaf(_))
  implicit val renderInt = makeRenderable[Int](i => Leaf(i.toString))
  implicit val renderBoolean = makeRenderable[Boolean](i => Leaf(i.toString))
  implicit val selfTree = makeRenderable[RenderTree](identity)

  /** Renderable for iterable things */
  implicit def makeTraversableRenderable[T](implicit elementRenderable: Renderable[T]): Renderable[Seq[T]] =
    new Renderable[Seq[T]] {
      override def buildRenderTree(value: Seq[T]): RenderTree = {
        val items = value.map(elementRenderable.buildRenderTree).toIndexedSeq
        SubTree(items)
      }
    }

  implicit def makeOptionRenderable[T: Renderable]: Renderable[Option[T]] = new Renderable[Option[T]] {
    override def buildRenderTree(value: Option[T]): RenderTree = {
      value match {
        case None    => Leaf("<empty>")
        case Some(v) => Renderable.buildRenderTree(v)
      }
    }
  }

  def renderPotentialJson(json: String): RenderTree = {
    io.circe.parser.parse(json).fold(e => Leaf("Invalid JSON"), ok => Leaf(ok.noSpaces))
  }
}
