package ai.mantik.ds.sql.parser

import ai.mantik.ds.sql.parser.AST.AnonymousReference
import org.parboiled2.{Parser, Rule1}

/** Defines an inner query parser, implemented by [[QueryParser]] */
trait InnerQueryParser {
  self: Parser =>

  /** Parse an inner query as expected in InnerQuery union InnerQuery */
  def UnionLikeInnerQuery: Rule1[AST.QueryNode]

  /** Parse an inner query as expected in SELECT [..] FROM InnerQuery */
  def SelectLikeInnerQuery: Rule1[AST.QueryNode]

  /** Parse an inner query as expeced in JOINs */
  def JoinLikeInnerQuery: Rule1[AST.QueryNode]
}

/** Implements InnerQueryParser with only support for anonymous queries */
trait AnonymousOnlyInnerQueryParser extends InnerQueryParser with ConstantParser {
  self: Parser =>

  override def UnionLikeInnerQuery: Rule1[AST.QueryNode] = AnonymousFrom

  override def SelectLikeInnerQuery: Rule1[AST.QueryNode] = AnonymousFrom

  override def JoinLikeInnerQuery: Rule1[AST.QueryNode] = AnonymousFrom

  def AnonymousFrom: Rule1[AnonymousReference] = rule {
    "$" ~ capture(Digits) ~ Whitespace ~> { s: String =>
      AnonymousReference(s.toInt)
    }
  }
}
