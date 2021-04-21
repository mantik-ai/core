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
