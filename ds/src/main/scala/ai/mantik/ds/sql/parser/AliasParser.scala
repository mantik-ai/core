package ai.mantik.ds.sql.parser

import org.parboiled2.{Parser, Rule1}

trait AliasParser {
  self: Parser with ExpressionParser =>

  def ParseAsAlias: Rule1[String] = {
    rule {
      keyword("as") ~ ParseShortAlias
    }
  }

  def ParseAsOrShortAlias: Rule1[String] = {
    rule {
      ParseAsAlias | ParseShortAlias
    }
  }

  def ParseShortAlias: Rule1[String] = {
    rule {
      UnescapedIdentifier ~> { identifier: AST.IdentifierNode =>
        identifier.name
      }
    }
  }

  def withOptionalAlias(query: AST.QueryNode, alias: Option[String]): AST.QueryNode = {
    alias
      .map { alias =>
        AST.AliasNode(query, alias)
      }
      .getOrElse(query)
  }

}
