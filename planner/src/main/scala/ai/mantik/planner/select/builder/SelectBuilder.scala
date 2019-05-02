package ai.mantik.planner.select.builder

import ai.mantik.ds.TabularData
import ai.mantik.planner.select.parser.AST
import ai.mantik.planner.select._

import scala.annotation.tailrec

private[select] object SelectBuilder {

  /**
   * Builds a select statement for a given input data
   * Returns either an error or a select statemnt
   */
  def buildSelect(input: TabularData, statement: String): Either[String, Select] = {
    for {
      node <- parser.SelectParser.parseSelectToNode(statement)
      build <- buildSelectFromParsed(input, node)
    } yield build
  }

  private def buildSelectFromParsed(input: TabularData, statement: AST.SelectNode): Either[String, Select] = {
    for {
      projections <- buildProjections(input, statement)
      selectors <- buildSelectors(input, statement)
    } yield Select(projections, selectors)
  }

  private def buildProjections(input: TabularData, statement: AST.SelectNode): Either[String, List[SelectProjection]] = {
    if (statement.isAll) {
      Right(
        input.columns.zipWithIndex.map {
          case ((columnName, columnType), idx) =>
            SelectProjection(columnName, ColumnExpression(idx, columnType))
        }.toList
      )
    } else {
      Utils.flatEither(
        statement.selectColumns.zipWithIndex.map {
          case (selectColumnNode, idx) =>
            buildProjection(input, selectColumnNode, idx)
        }
      )
    }
  }

  private def buildSelectors(input: TabularData, statement: AST.SelectNode): Either[String, List[Condition]] = {
    statement.where match {
      case None => Right(Nil)
      case Some(expression) =>
        SelectorBuilder.convertSelector(input, expression)
    }
  }

  private def buildProjection(input: TabularData, node: AST.SelectColumnNode, idx: Int): Either[String, SelectProjection] = {
    val name = guessName(node, idx)
    for {
      expression <- ExpressionBuilder.convertExpression(input, node.expression)
    } yield SelectProjection(name, expression)
  }

  private def guessName(node: AST.SelectColumnNode, idx: Int): String = {
    node.as match {
      case Some(identifier) => identifier.name
      case None =>
        guessName(node.expression).getOrElse(
          // choose artificial name
          "$" + (idx + 1).toString // Starting with 1 looks better
        )
    }
  }

  @tailrec
  private def guessName(node: AST.ExpressionNode): Option[String] = {
    node match {
      case id: AST.IdentifierNode => Some(id.name)
      case cast: AST.CastNode =>
        guessName(cast.expression)
      case _ => None
    }
  }

}
