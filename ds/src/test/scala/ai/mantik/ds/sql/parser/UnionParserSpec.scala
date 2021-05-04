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

import ai.mantik.ds.sql.parser.AST.UnionNode
import org.parboiled2.{Parser, ParserInput}

class UnionParserSpec extends ParserTestBase {
  class ParserImpl(val input: ParserInput) extends Parser with UnionParser with AnonymousOnlyInnerQueryParser

  override protected def makeParser(s: String): ParserImpl = new ParserImpl(s)

  def parseUnionTest(s: String, expected: UnionNode): Unit = {
    it should s"parse ${s}" in {
      testEquality(_.Union, s, expected)
    }
  }

  parseUnionTest(
    "$0 UNION $1",
    AST.UnionNode(
      AST.AnonymousReference(0),
      AST.AnonymousReference(1),
      false
    )
  )

  parseUnionTest(
    "$1 UNION ALL $2",
    AST.UnionNode(
      AST.AnonymousReference(1),
      AST.AnonymousReference(2),
      true
    )
  )
}
