package ai.mantik.ds.sql.parser

import ai.mantik.ds.sql.parser.AST.{ ExpressionNode, SelectColumnNode, SelectNode }
import org.parboiled2.{ ParseError, Parser, ParserInput, Rule1 }

import scala.collection.immutable

object SelectParser {

  def parseSelectToNode(select: String): Either[String, SelectNode] = {
    import Parser.DeliveryScheme.Either
    val parser = new SelectParser(select)
    val result = parser.Select.run()
    result match {
      case Left(error) =>
        val formatted = parser.formatError(error)
        Left(formatted)
      case Right(ok) =>
        Right(ok)
    }
  }
}

private[parser] class SelectParser(val input: ParserInput) extends Parser with ExpressionParser {

  def Select: Rule1[SelectNode] = rule {
    (keyword("select") ~ SelectColumns ~ optional(keyword("where") ~ Expression)) ~> { (columns, where) =>
      SelectNode(columns, where)
    }
  }

  def SelectColumns: Rule1[List[SelectColumnNode]] = rule {
    SelectAllColumns | SelectSomeColumns
  }

  def SelectAllColumns: Rule1[List[SelectColumnNode]] = rule {
    symbolw('*') ~ push(Nil: List[SelectColumnNode])
  }

  def SelectSomeColumns: Rule1[List[SelectColumnNode]] = rule {
    oneOrMore(SelectColumn).separatedBy(symbolw(',')) ~> { elements: immutable.Seq[SelectColumnNode] =>
      elements.toList
    }
  }

  def SelectColumn: Rule1[SelectColumnNode] = rule {
    (Expression ~ optional(keyword("as") ~ Identifier)) ~> { (expression, asValue) =>
      AST.SelectColumnNode(expression, asValue)
    }
  }
}
