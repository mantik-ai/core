package ai.mantik.ds.sql.parser

import ai.mantik.ds.sql.parser.AST._
import org.parboiled2.{ Parser, ParserInput }

class SelectParserSpec extends ParserTestBase {

  class FullParser(val input: ParserInput) extends Parser with SelectParser with AnonymousOnlyInnerQueryParser

  override type ParserImpl = FullParser
  override protected def makeParser(s: String) = new FullParser(s)

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

  parseSelectTest("select * from $1", SelectNode(from = Some(AnonymousReference(1))))

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

  parseSelectTest("select baz,biz + 1 from $0 where a + b = c", SelectNode(
    List(
      SelectColumnNode(IdentifierNode("baz")),
      SelectColumnNode(BinaryOperationNode("+", IdentifierNode("biz"), NumberNode(BigDecimal(1))))
    ),
    Some(
      BinaryOperationNode("=", BinaryOperationNode("+", IdentifierNode("a"), IdentifierNode("b")), IdentifierNode("c"))
    ),
    Some(AnonymousReference(0))
  )
  )
}
