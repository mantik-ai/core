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
