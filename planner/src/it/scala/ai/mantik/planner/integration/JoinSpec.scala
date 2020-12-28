package ai.mantik.planner.integration

import ai.mantik.ds.{FundamentalType, Nullable, TabularData}
import ai.mantik.ds.element.{Bundle, NullElement, Primitive, TabularBundle, TabularRow}
import ai.mantik.ds.sql.{Join, JoinType, Query}
import ai.mantik.planner.DataSet

class JoinSpec extends IntegrationTestBase {
  val sample1 = Bundle.buildColumnWise
    .withPrimitives("x", 1, 2, 3)
    .withPrimitives("y", "a", "b", "c")
    .result

  val sample2 = Bundle.buildColumnWise
    .withPrimitives("x", 1, 2, 4)
    .withPrimitives("z", "Hello", "World", "!")
    .result

  it should "do a simple inner join" in {
    val result = DataSet.literal(sample1).join(DataSet.literal(sample2), Seq("x")).fetch.run()
    result.sorted shouldBe Bundle.buildColumnWise
      .withPrimitives("x", 1, 2)
      .withPrimitives("y", "a", "b")
      .withPrimitives("z", "Hello", "World")
      .result
      .sorted
  }

  it should "do a simple left join" in {
    val result = DataSet.literal(sample1).join(DataSet.literal(sample2), Seq("x"), JoinType.Left).fetch.run()
    result.sorted shouldBe Bundle.build(
      TabularData(
        "x" -> FundamentalType.Int32,
        "y" -> FundamentalType.StringType,
        "z" -> Nullable(FundamentalType.StringType)
      )
    ).row(1, "a", "Hello")
      .row(2, "b", "World")
      .row(3, "c", NullElement)
      .result
      .sorted
  }

  it should "do a simple right join" in {
    val result = DataSet.literal(sample1).join(DataSet.literal(sample2), Seq("x"), JoinType.Right).fetch.run()
    result.sorted shouldBe Bundle.build(
      TabularData(
        "y" -> Nullable(FundamentalType.StringType),
        "x" -> FundamentalType.Int32,
        "z" -> FundamentalType.StringType
      )
    ).row("a", 1, "Hello")
      .row("b", 2, "World")
      .row(NullElement, 4, "!")
      .result
      .sorted
  }

  it should "do a simple outer join" in {
    val result = DataSet.literal(sample1).join(DataSet.literal(sample2), Seq("x"), JoinType.Outer).fetch.run()
    val expected = Query.run("SELECT * FROM $0 FULL OUTER JOIN $1 USING x", sample1, sample2).forceRight
    result.sorted shouldBe expected.sorted
  }

  it should "do a simple cartesian join" in {
    val result = DataSet.literal(sample1).join(DataSet.literal(sample2), Nil, JoinType.Inner).fetch.run()
    val expected = Query.run("SELECT * FROM $0 CROSS JOIN $1", sample1, sample2).forceRight
    result.sorted shouldBe expected.sorted
  }

  it should "work in complexer queries" in {
    val result = DataSet.query(
      "SELECT l.x, r.z FROM $0 AS l LEFT JOIN $1 AS r ON l.x = r.x", DataSet.literal(sample1), DataSet.literal(sample2)
    )
    result.forceDataTypeTabular() shouldBe TabularData(
      "x" -> FundamentalType.Int32,
      "z" -> Nullable(FundamentalType.StringType)
    )
    val fetched = result.fetch.run()

    fetched.sorted shouldBe Bundle.build(
      result.forceDataTypeTabular()
    ).row(1, "Hello")
      .row(2, "World")
      .row(3, NullElement)
      .result
  }

  // Disabled as it takes time
  ignore should "work with some more elements" in {
    // Measurement, took 15sec (Go in Docker), Scala 2s
    val CountLeft  = 700000
    val CountRight = 500000
    val left = TabularBundle(
      TabularData(
        "id" -> FundamentalType.Int32,
        "name" -> FundamentalType.StringType
      ),
      rows = (for (i <- 0 until CountLeft) yield TabularRow(
        Primitive(i), Primitive(s"Name${i}")
      )).toVector
    )
    val right = TabularBundle(
      TabularData(
        "id" -> FundamentalType.Int32,
        "address" -> FundamentalType.StringType
      ),
      rows = (for (i <- 0 until CountLeft) yield {
        val rightId = CountRight - i
        TabularRow(
          Primitive(rightId), Primitive(s"Address${rightId}")
        )
      }).toVector
    )

    val leftDs = DataSet.literal(left)
    val rightDs = DataSet.literal(right)
    val statement = "SELECT l.id, l.name, r.address FROM $0 AS l LEFT JOIN $1 AS r ON l.id = r.id"
    val joined = DataSet.query(
      statement, leftDs, rightDs
    )
    val t0 = System.currentTimeMillis()
    val result = joined.fetch.run()
    val t1 = System.currentTimeMillis()
    logger.info(s"Joining via bridge took ${t1 - t0}ms")

    result.model shouldBe TabularData(
      "id" -> FundamentalType.Int32,
      "name" -> FundamentalType.StringType,
      "address" -> Nullable(FundamentalType.StringType)
    )
    result.rows.size shouldBe CountLeft

    val t2 = System.currentTimeMillis()
    val scalaResult = Query.run(statement, left, right).forceRight
    val t3 = System.currentTimeMillis()
    logger.info(s"Joining via scala took ${t3 - t2}ms")
  }
}
