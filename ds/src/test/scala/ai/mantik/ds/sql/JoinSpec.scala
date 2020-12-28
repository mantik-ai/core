package ai.mantik.ds.sql

import ai.mantik.ds.element.{ Bundle, NullElement, SomeElement, TabularBundle }
import ai.mantik.ds.sql.JoinCondition.UsingColumn
import ai.mantik.ds.sql.run.Compiler
import ai.mantik.ds.{ FundamentalType, Nullable, TabularData }
import ai.mantik.testutils.{ AkkaSupport, TestBase }
import akka.stream.scaladsl.Source
import akka.util.ByteString

class JoinSpec extends TestBase with AkkaSupport {

  "resultingTabularType" should "work" in {
    val left = AnonymousInput(
      TabularData(
        "x" -> FundamentalType.Int32,
        "y" -> FundamentalType.StringType
      )
    )

    val right = AnonymousInput(
      TabularData(
        "x" -> FundamentalType.Int64,
        "z" -> Nullable(FundamentalType.StringType)
      )
    )

    val join1 = Join(
      left, right, JoinType.Left, JoinCondition.Cross
    )

    // More Tests can be foujnd in JoinBuilder.innerTabularData

    join1.resultingTabularType shouldBe TabularData(
      "x" -> FundamentalType.Int32,
      "y" -> FundamentalType.StringType,
      "x0" -> Nullable(FundamentalType.Int64),
      "z" -> Nullable(FundamentalType.StringType)
    )

    val join2 = Join(
      left, right, JoinType.Left, JoinCondition.Using(Vector(UsingColumn("x", false, 0, 0, 2, FundamentalType.Int32)))
    )

    join2.resultingTabularType shouldBe TabularData(
      "x" -> FundamentalType.Int32,
      "y" -> FundamentalType.StringType,
      "z" -> Nullable(FundamentalType.StringType)
    )
  }

  val example1 = Bundle.build(
    TabularData(
      "x" -> FundamentalType.Int32,
      "y" -> FundamentalType.Int32
    )
  ).row(1, 2)
    .row(2, 3)
    .row(3, 2)
    .result

  val example2 = Bundle.build(
    TabularData(
      "x" -> FundamentalType.Int32,
      "z" -> FundamentalType.StringType
    )
  ).row(1, "Hello")
    .row(3, "World")
    .row(4, "Boo!")
    .result

  val example3 = Bundle.build(
    example2.model
  ).row(1, "Hello").row(4, "Boo").result

  private implicit val sqlContext = SqlContext(Vector(example1.model, example2.model, example3.model))

  /**
   * Sort a bundle by their message pack serialization.
   * Note: this is a hack with horrible performance
   */
  private def sortBundle(tabularBundle: TabularBundle): TabularBundle = {
    val rows = collectSource(tabularBundle.encode(false))
    val sortedRows = rows.sorted
    val back = await(Source(sortedRows.toVector).runWith(Bundle.fromStreamWithoutHeader(tabularBundle.model)))
    back.asInstanceOf[TabularBundle]
  }

  private implicit val byteStringOrdering: Ordering[ByteString] = Ordering.fromLessThan { (left, right) =>
    (left.length, right.length) match {
      case (a, b) if a < b => true
      case (a, b) if a > b => false
      case (_, _) => left.zipWithIndex.collectFirst {
        case (byte, n) if byte != right(n) => (byte, right(n))
      } match {
        case None                        => false
        case Some((leftByte, rightByte)) => leftByte < rightByte
      }
    }
  }

  private def joinTest(sql: String)(expected: TabularBundle): Unit = {
    sql should "work" in {
      val built = Query.parse(sql).forceRight
      val result = built.run(example1, example2, example3).forceRight
      result.model shouldBe expected.model
      val orderedGot = sortBundle(result)
      val orderedExpected = sortBundle(expected)
      orderedGot shouldBe orderedExpected
    }
  }

  joinTest("SELECT * FROM $0 JOIN $1 USING x") {
    Bundle.build(
      TabularData(
        "x" -> FundamentalType.Int32,
        "y" -> FundamentalType.Int32,
        "z" -> FundamentalType.StringType
      )
    ).row(1, 2, "Hello")
      .row(3, 2, "World")
      .result
  }

  joinTest("SELECT l.x, l.y, r.z FROM $0 AS l JOIN $1 AS r ON l.x = r.x") {
    Bundle.build(
      TabularData(
        "x" -> FundamentalType.Int32,
        "y" -> FundamentalType.Int32,
        "z" -> FundamentalType.StringType
      )
    ).row(1, 2, "Hello")
      .row(3, 2, "World")
      .result
  }

  joinTest("SELECT l.x, l.y, r.z FROM $0 AS l JOIN $1 AS r ON l.x = 1 AND l.x = r.x") {
    Bundle.build(
      TabularData(
        "x" -> FundamentalType.Int32,
        "y" -> FundamentalType.Int32,
        "z" -> FundamentalType.StringType
      )
    ).row(1, 2, "Hello")
      .result
  }

  joinTest("SELECT l.x, l.y, r.z FROM $0 AS l JOIN $1 AS r ON r.x = 1 AND l.x = r.x") {
    Bundle.build(
      TabularData(
        "x" -> FundamentalType.Int32,
        "y" -> FundamentalType.Int32,
        "z" -> FundamentalType.StringType
      )
    ).row(1, 2, "Hello")
      .result
  }

  joinTest("SELECT * FROM $0 LEFT JOIN $1 USING x") {
    Bundle.build(
      TabularData(
        "x" -> FundamentalType.Int32,
        "y" -> FundamentalType.Int32,
        "z" -> Nullable(FundamentalType.StringType)
      )
    ).row(1, 2, "Hello")
      .row(2, 3, NullElement)
      .row(3, 2, "World")
      .result
  }

  joinTest("SELECT * FROM $0 RIGHT JOIN $1 USING x") {
    Bundle.build(
      TabularData(
        "y" -> Nullable(FundamentalType.Int32),
        "x" -> FundamentalType.Int32,
        "z" -> FundamentalType.StringType
      )
    ).row(2, 1, "Hello")
      .row(2, 3, "World")
      .row(NullElement, 4, "Boo!")
      .result
  }

  joinTest("SELECT * FROM $0 FULL OUTER JOIN $1 USING x") {
    Bundle.build(
      TabularData(
        "x" -> Nullable(FundamentalType.Int32),
        "y" -> Nullable(FundamentalType.Int32),
        "z" -> Nullable(FundamentalType.StringType)
      )
    ).row(1, 2, "Hello")
      .row(2, 3, NullElement)
      .row(3, 2, "World")
      .row(4, NullElement, "Boo!") // TODO: This value is missing and tricky to resolve
      .result
  }

  joinTest("SELECT * FROM $0 CROSS JOIN $2") {
    Bundle.build(
      TabularData(
        "x" -> FundamentalType.Int32,
        "y" -> FundamentalType.Int32,
        "x0" -> FundamentalType.Int32,
        "z" -> FundamentalType.StringType
      )
    ).row(1, 2, 1, "Hello")
      .row(1, 2, 4, "Boo")
      .row(2, 3, 1, "Hello")
      .row(2, 3, 4, "Boo")
      .row(3, 2, 1, "Hello")
      .row(3, 2, 4, "Boo")
      .result
  }
}
