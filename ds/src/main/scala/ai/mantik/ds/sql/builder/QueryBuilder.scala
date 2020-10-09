package ai.mantik.ds.sql.builder

import ai.mantik.ds.sql.{ AnonymousInput, Query, SqlContext, Union }
import ai.mantik.ds.sql.parser.{ AST, QueryParser }

/** Builds queries from AST Nodes */
private[sql] object QueryBuilder {

  /** Build a query from a statement string. */
  def buildQuery(statement: String)(implicit context: SqlContext): Either[String, Query] = {
    for {
      queryNode <- QueryParser.parseQueryNode(statement)
      query <- buildQueryFromParsed(queryNode)
    } yield query
  }

  /** Build a Query from a parsed entry. */
  def buildQueryFromParsed(query: AST.QueryNode)(implicit context: SqlContext): Either[String, Query] = {
    query match {
      case a: AST.AnonymousReference =>
        buildAnonymousInput(a)
      case s: AST.SelectNode =>
        SelectBuilder.buildSelectFromParsed(s)
      case u: AST.UnionNode =>
        buildUnionFromParsed(u)
    }
  }

  def buildAnonymousInput(a: AST.AnonymousReference)(implicit context: SqlContext): Either[String, AnonymousInput] = {
    if (context.anonymous.isDefinedAt(a.id)) {
      val dataType = context.anonymous(a.id)
      Right(AnonymousInput(dataType, a.id))
    } else {
      Left(s"Could not resolve anonymous input ${a}")
    }
  }

  def buildUnionFromParsed(union: AST.UnionNode)(implicit context: SqlContext): Either[String, Union] = {
    for {
      leftQuery <- buildQueryFromParsed(union.left)
      rightQuery <- buildQueryFromParsed(union.right)
      _ <- validateSameType(leftQuery, rightQuery)
    } yield Union(leftQuery, rightQuery, union.all)
  }

  private def validateSameType(left: Query, right: Query): Either[String, Unit] = {
    if (left.resultingType == right.resultingType) {
      Right(())
    } else {
      Left(s"Type mismatch, left: ${left.resultingType}, right: ${right.resultingType}")
    }
  }
}
