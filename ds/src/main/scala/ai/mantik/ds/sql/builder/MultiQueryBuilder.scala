package ai.mantik.ds.sql.builder

import ai.mantik.ds.sql.parser.AST
import ai.mantik.ds.sql.parser.QueryParser
import ai.mantik.ds.sql.{ MultiQuery, SingleQuery, Split, SqlContext }
import cats.implicits._

import scala.util.control.NonFatal

private[mantik] object MultiQueryBuilder {
  def buildQuery(statement: String)(implicit context: SqlContext): Either[String, MultiQuery] = {
    for {
      queryNode <- QueryParser.parseMultiQuery(statement)
      query <- buildQueryFromParsed(queryNode)
    } yield query
  }

  def buildQueryFromParsed(parsed: AST.MultiQueryNode)(implicit context: SqlContext): Either[String, MultiQuery] = {
    parsed match {
      case AST.SingleQuery(query) =>
        QueryBuilder.buildQueryFromParsed(query).map(SingleQuery(_))
      case s: AST.Split =>
        buildSplit(s)
    }
  }

  def buildSplit(split: AST.Split)(implicit context: SqlContext): Either[String, Split] = {
    for {
      inner <- QueryBuilder.buildQueryFromParsed(split.query)
      fractions <- wrapNumberDecodingErrors("fractions", split.fractions.map(x => x.value.toDouble))
      shuffle <- split.shuffleSeed.map { seed =>
        wrapNumberDecodingErrors("shuffle", seed.value.toLongExact)
      }.sequence
    } yield {
      Split(inner, fractions, shuffle)
    }
  }

  private def wrapNumberDecodingErrors[T](name: String, f: => T): Either[String, T] = {
    try {
      Right(f)
    } catch {
      case NonFatal(e) => Left(s"Error decoding ${name}, ${e.getMessage}")
    }
  }
}
