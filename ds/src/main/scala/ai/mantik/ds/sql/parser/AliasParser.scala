/*
 * This file is part of the Mantik Project.
 * Copyright (c) 2020-2021 Mantik UG (Haftungsbeschränkt)
 * Authors: See AUTHORS file
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.
 *
 * Additionally, the following linking exception is granted:
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with other code, such other code is not for that reason
 * alone subject to any of the requirements of the GNU Affero GPL
 * version 3.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license.
 */
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
