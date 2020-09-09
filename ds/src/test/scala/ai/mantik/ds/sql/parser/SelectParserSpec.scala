package ai.mantik.ds.sql.parser

import ai.mantik.ds.sql.parser.AST._

class SelectParserSpec extends ParserTestBase {

  override type ParserImpl = SelectParser

  override protected def makeParser(s: String) = new SelectParser(s)

  def parseSelectTest(s: String, expected: SelectNode): Unit = {
    it should s"parse ${s}" in {
      testEquality(_.Select, s, expected)
    }
  }

  parseSelectTest("select 1234", SelectNode(List(SelectColumnNode(NumberNode(1234)))))

  parseSelectTest("select 1234, false", SelectNode(List(
    SelectColumnNode(NumberNode(1234)),
    SelectColumnNode(BoolNode(false))))
  )

  parseSelectTest("select foo,\"bar\"", SelectNode(List(
    SelectColumnNode(IdentifierNode("foo")),
    SelectColumnNode(IdentifierNode("bar", ignoreCase = false))
  )))

  parseSelectTest("select *", SelectNode())

  parseSelectTest("select foo as bar", SelectNode(
    List(
      SelectColumnNode(IdentifierNode("foo"), as = Some(IdentifierNode("bar")))
    )
  ))

  parseSelectTest("select foo as bar, baz", SelectNode(List(
    SelectColumnNode(IdentifierNode("foo"), as = Some(IdentifierNode("bar"))),
    SelectColumnNode(IdentifierNode("baz"))
  )))

  parseSelectTest("select foo where a", SelectNode(
    List(SelectColumnNode(IdentifierNode("foo"))),
    Some(
      IdentifierNode("a")
    ))
  )

  parseSelectTest("select foo where a = b", SelectNode(
    List(SelectColumnNode(IdentifierNode("foo"))),
    Some(
      BinaryOperationNode("=", IdentifierNode("a"), IdentifierNode("b"))
    ))
  )
}
