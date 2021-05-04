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

import ai.mantik.ds.sql.parser.AST.{JoinCondition, JoinNode, JoinType}
import org.parboiled2.{Parser, Rule1}

trait JoinParser extends ExpressionParser with InnerQueryParser {
  this: Parser =>

  def Join: Rule1[JoinNode] = {
    rule {
      NonCrossJoin | CrossJoin
    }
  }

  private def NonCrossJoin: Rule1[AST.JoinNode] = {
    rule {
      JoinLikeInnerQuery ~ JoinType ~ JoinLikeInnerQuery ~ JoinCondition ~> {
        (left: AST.QueryNode, joinType: AST.JoinType, right: AST.QueryNode, condition: JoinCondition) =>
          JoinNode(left, right, joinType, condition)
      }
    }
  }

  private def CrossJoin: Rule1[AST.JoinNode] = {
    rule {
      JoinLikeInnerQuery ~ keyword("cross") ~ keyword("join") ~ JoinLikeInnerQuery ~> {
        (left: AST.QueryNode, right: AST.QueryNode) =>
          AST.JoinNode(left, right, AST.JoinType.Inner, AST.JoinCondition.Cross)
      }
    }
  }

  private def JoinType: Rule1[AST.JoinType] = {
    rule {
      InnerJoinType | LeftOuterJoinType | RightOuterJoinType | FullOuterJoinType
    }
  }

  private def InnerJoinType: Rule1[AST.JoinType] = {
    rule {
      optional(keyword("inner")) ~ keyword("join") ~ push(AST.JoinType.Inner)
    }
  }

  private def LeftOuterJoinType: Rule1[AST.JoinType] = {
    rule {
      keyword("left") ~ optional(keyword("outer")) ~ keyword("join") ~ push(AST.JoinType.Left)
    }
  }

  private def RightOuterJoinType: Rule1[AST.JoinType] = {
    rule {
      keyword("right") ~ optional(keyword("outer")) ~ keyword("join") ~ push(AST.JoinType.Right)
    }
  }

  private def FullOuterJoinType: Rule1[AST.JoinType] = {
    rule {
      keyword("full") ~ optional(keyword("outer")) ~ keyword("join") ~ push(AST.JoinType.Outer)
    }
  }

  private def JoinCondition: Rule1[AST.JoinCondition] = {
    rule {
      JoinOn | JoinUsing
    }
  }

  private def JoinOn: Rule1[AST.JoinCondition.On] = {
    rule {
      keyword("on") ~ Expression ~> { x => AST.JoinCondition.On(x) }
    }
  }

  private def JoinUsing: Rule1[AST.JoinCondition.Using] = {
    rule {
      keyword("using") ~ Identifier ~ zeroOrMore(keyword(",") ~ Identifier) ~> {
        (i0: AST.IdentifierNode, inext: Seq[AST.IdentifierNode]) =>
          AST.JoinCondition.Using(i0 +: inext.toVector)
      }
    }
  }
}
