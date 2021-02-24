package ai.mantik.ds.sql.parser

import org.parboiled2.{ Parser, ParserInput, Rule1 }

class MultiQueryParserSpec extends ParserTestBase {
  class FullParser(val input: ParserInput)
    extends Parser with SelectParser with AnonymousOnlyInnerQueryParser with MultiQueryParser {
    override def BracketInnerQuery: Rule1[AST.QueryNode] = rule {
      symbolw('(') ~ Query ~ symbolw(')')
    }

    override def Query: Rule1[AST.QueryNode] = rule {
      AnonymousFrom | Select
    }
  }

  override type ParserImpl = FullParser

  override protected def makeParser(s: String): FullParser = new FullParser(s)

  private val simpleSelect = (
    AST.SelectNode(
      selectColumns = Vector(
        AST.SelectColumnNode(
          AST.IdentifierNode("x")
        )
      ),
      from = Some(
        AST.AnonymousReference(0)
      )
    )
  )

  def parseTest(s: String, node: AST.MultiQueryNode): Unit = {
    it should s"parse ${s}" in {
      testEquality(_.MultiQueryEOI, s, node)
    }
  }

  parseTest("SELECT x FROM $0", AST.SingleQuery(simpleSelect))

  parseTest(
    "SPLIT (SELECT x FROM $0) AT 0.1, 2, 0.3 WITH SHUFFLE 4",
    AST.Split(
      simpleSelect,
      fractions = Vector(AST.NumberNode(0.1), AST.NumberNode(2), AST.NumberNode(0.3)),
      shuffleSeed = Some(
        AST.NumberNode(4)
      )
    )
  )

  parseTest(
    "SPLIT (SELECT x FROM $0) AT 0.2",
    AST.Split(
      simpleSelect,
      fractions = Vector(AST.NumberNode(0.2)),
      shuffleSeed = None
    )
  )
}
