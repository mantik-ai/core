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
package ai.mantik.ds.sql.parser

import ai.mantik.ds.sql.parser.AST.UnionNode
import org.parboiled2.{Parser, Rule1}

private[parser] trait UnionParser extends ExpressionParser with InnerQueryParser {
  this: Parser =>

  def Union: Rule1[UnionNode] = {
    rule {
      UnionLikeInnerQuery ~ oneOrMore(parseUnionTail) ~> { (start, next) =>
        buildUnionNode(start, next)
      }
    }
  }

  private case class UnionTail(
      all: Boolean,
      right: AST.QueryNode
  )

  private def parseUnionTail: Rule1[UnionTail] = rule {
    keyword("union") ~ optionalKeyword("all") ~ UnionLikeInnerQuery ~> { (all, right) =>
      UnionTail(all, right)
    }
  }

  private def buildUnionNode(start: AST.QueryNode, next: scala.collection.immutable.Seq[UnionTail]): AST.UnionNode = {
    val first = next.head
    next.tail.foldLeft(
      AST.UnionNode(start, first.right, first.all)
    ) { case (current, next) =>
      AST.UnionNode(current, next.right, next.all)
    }
  }
}
