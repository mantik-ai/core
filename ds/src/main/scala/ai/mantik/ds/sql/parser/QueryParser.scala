package ai.mantik.ds.sql.parser

import ai.mantik.ds.sql.parser.AST.{ MultiQueryNode, QueryNode, SelectNode }
import org.parboiled2.{ ParseError, Parser, ParserInput, Rule1 }
import Parser.DeliveryScheme.Either

import scala.collection.immutable

object QueryParser {

  /** Parse a SELECT statement into a SelectNode */
  def parseSelectToNode(select: String): Either[String, SelectNode] = {
    parseQueryNode(select).right.flatMap {
      case s: SelectNode => Right(s)
      case other         => Left(s"Expected SELECT, got ${other.getClass.getSimpleName}")
    }
  }

  /** Parse a Query */
  def parseQueryNode(statement: String): Either[String, QueryNode] = {
    val parser = new QueryParser(statement)
    val result = parser.FullQuery.run()
    decodeResult(parser, result)
  }

  /** Parse a Multi Query */
  def parseMultiQuery(statement: String): Either[String, MultiQueryNode] = {
    val parser = new QueryParser(statement)
    val result = parser.MultiQueryEOI.run()
    decodeResult(parser, result)
  }

  private def decodeResult[T](p: Parser, result: Either[ParseError, T]): Either[String, T] = {
    result.left.map { err =>
      p.formatError(err)
    }
  }
}

private[parser] class QueryParser(val input: ParserInput) extends Parser
  with SelectParser
  with UnionParser
  with JoinParser
  with AliasParser
  with MultiQueryParser
  with AnonymousOnlyInnerQueryParser {

  def FullQuery: Rule1[AST.QueryNode] = rule {
    Query ~ optional(ParseAsAlias) ~ EOI ~> { withOptionalAlias(_, _) }
  }

  def Query: Rule1[AST.QueryNode] = rule {
    Union | Join | AnonymousFrom | Select
  }

  override def SelectLikeInnerQuery: Rule1[QueryNode] = rule {
    Join | AnonymousFromMaybeAliases | BracketInnerQuery
  }

  override def UnionLikeInnerQuery: Rule1[QueryNode] = rule {
    AnonymousFromMaybeAliases | Select | BracketInnerQuery
  }

  override def JoinLikeInnerQuery: Rule1[QueryNode] = rule {
    AnonymousFromMaybeAliases | BracketInnerQuery
  }

  private def AnonymousFromMaybeAliases: Rule1[QueryNode] = rule {
    AnonymousFrom ~ optional(ParseAsAlias) ~> { withOptionalAlias(_, _) }
  }

  def BracketInnerQuery: Rule1[QueryNode] = rule {
    symbolw('(') ~ Query ~ symbolw(')')
  }
}
