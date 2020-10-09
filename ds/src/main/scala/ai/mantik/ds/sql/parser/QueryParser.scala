package ai.mantik.ds.sql.parser

import ai.mantik.ds.sql.parser.AST.{ QueryNode, SelectNode }
import org.parboiled2.{ Parser, ParserInput, Rule1 }

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
    import Parser.DeliveryScheme.Either
    val parser = new QueryParser(statement)
    val result = parser.FullQuery.run()
    result match {
      case Left(error) =>
        val formatted = parser.formatError(error)
        Left(formatted)
      case Right(ok) =>
        Right(ok)
    }
  }
}

private[parser] class QueryParser(val input: ParserInput) extends Parser
  with SelectParser
  with UnionParser
  with AnonymousOnlyInnerQueryParser {

  def FullQuery: Rule1[AST.QueryNode] = rule {
    Query ~ EOI
  }

  def Query: Rule1[AST.QueryNode] = rule {
    Union | AnonymousFrom | Select
  }

  override def SelectLikeInnerQuery: Rule1[QueryNode] = rule {
    AnonymousFrom | BracketInnerQuery
  }

  override def UnionLikeInnerQuery: Rule1[QueryNode] = rule {
    AnonymousFrom | Select
  }

  def BracketInnerQuery: Rule1[QueryNode] = rule {
    symbolw('(') ~ Query ~ symbolw(')')
  }
}
