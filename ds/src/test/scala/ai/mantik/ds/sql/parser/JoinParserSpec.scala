package ai.mantik.ds.sql.parser

import ai.mantik.ds.sql.parser.AST.JoinNode
import org.parboiled2.{Parser, ParserInput}

class JoinParserSpec extends ParserTestBase {
  class ParserImpl(val input: ParserInput) extends Parser with JoinParser with AnonymousOnlyInnerQueryParser

  override protected def makeParser(s: String): ParserImpl = new ParserImpl(s)

  def parseJoinTest(candidates: Seq[String], expected: JoinNode): Unit = {
    candidates.foreach { s =>
      it should s"parse ${s}" in {
        testEquality(_.Join, s, expected)
      }
    }
  }

  parseJoinTest(
    Seq("$0 JOIN $1 ON a = b", "$0 INNER JOIN $1 ON a = b"),
    AST.JoinNode(
      AST.AnonymousReference(0),
      AST.AnonymousReference(1),
      AST.JoinType.Inner,
      AST.JoinCondition.On(
        AST.BinaryOperationNode("=", AST.IdentifierNode("a"), AST.IdentifierNode("b"))
      )
    )
  )

  parseJoinTest(
    Seq("$0 CROSS JOIN $1"),
    AST.JoinNode(
      AST.AnonymousReference(0),
      AST.AnonymousReference(1),
      AST.JoinType.Inner,
      AST.JoinCondition.Cross
    )
  )

  parseJoinTest(
    Seq("$0 JOIN $1 USING a", "$0 INNER JOIN $1 USING a"),
    AST.JoinNode(
      AST.AnonymousReference(0),
      AST.AnonymousReference(1),
      AST.JoinType.Inner,
      AST.JoinCondition.Using(
        Vector(AST.IdentifierNode("a"))
      )
    )
  )

  parseJoinTest(
    Seq("$0 LEFT JOIN $1 ON true", "$0 LEFT OUTER JOIN $1 ON true"),
    AST.JoinNode(
      AST.AnonymousReference(0),
      AST.AnonymousReference(1),
      AST.JoinType.Left,
      AST.JoinCondition.On(
        AST.BoolNode(true)
      )
    )
  )

  parseJoinTest(
    Seq("$0 RIGHT JOIN $1 USING a,b", "$0 RIGHT OUTER JOIN $1 USING a,b"),
    AST.JoinNode(
      AST.AnonymousReference(0),
      AST.AnonymousReference(1),
      AST.JoinType.Right,
      AST.JoinCondition.Using(
        Vector(AST.IdentifierNode("a"), AST.IdentifierNode("b"))
      )
    )
  )

  parseJoinTest(
    Seq("$0 FULL OUTER JOIN $1 USING \"A\"", "$0 FULL JOIN $1 USING \"A\""),
    AST.JoinNode(
      AST.AnonymousReference(0),
      AST.AnonymousReference(1),
      AST.JoinType.Outer,
      AST.JoinCondition.Using(
        Vector(AST.IdentifierNode("A", ignoreCase = false))
      )
    )
  )
}
