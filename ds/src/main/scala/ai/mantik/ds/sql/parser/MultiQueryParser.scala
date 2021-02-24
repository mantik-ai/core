package ai.mantik.ds.sql.parser

import ai.mantik.ds.sql.parser.AST.QueryNode
import org.parboiled2.{ Parser, Rule1 }

import scala.collection.immutable

/**
 * Parses Multi queries
 * (Special queries which can return multiple tables)
 */
trait MultiQueryParser {
  self: Parser with ConstantParser with ExpressionParser =>

  def BracketInnerQuery: Rule1[QueryNode]

  def Query: Rule1[AST.QueryNode]

  def MultiQueryEOI: Rule1[AST.MultiQueryNode] = rule {
    MultiQuery ~ EOI
  }

  def MultiQuery: Rule1[AST.MultiQueryNode] = rule {
    Split | SingleQuery
  }

  def SingleQuery: Rule1[AST.SingleQuery] = rule {
    Query ~> { query =>
      AST.SingleQuery(query)
    }
  }

  def Split: Rule1[AST.Split] = rule {
    keyword("split") ~
      BracketInnerQuery ~
      keyword("at") ~
      oneOrMore(NumberExpression).separatedBy(symbolw(',')) ~
      optional(
        keyword("with") ~ keyword("shuffle") ~ NumberExpression
      ) ~> { (innerQuery: AST.QueryNode, splits: immutable.Seq[AST.NumberNode], shuffle: Option[AST.NumberNode]) =>
          AST.Split(
            innerQuery,
            splits.toVector,
            shuffle
          )
        }
  }
}
