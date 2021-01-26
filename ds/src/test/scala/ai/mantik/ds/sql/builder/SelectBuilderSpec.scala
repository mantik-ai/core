package ai.mantik.ds.sql.builder

import ai.mantik.ds.FundamentalType.{ StringType, fromName }
import ai.mantik.ds.element.{ Bundle, Primitive, SingleElementBundle, TabularBundle }
import ai.mantik.ds.sql._
import ai.mantik.ds.testutil.TestBase
import ai.mantik.ds.{ ArrayT, FundamentalType, Nullable, Struct, TabularData }

class SelectBuilderSpec extends TestBase {

  val simpleInput = TabularData(
    "x" -> FundamentalType.Int32,
    "y" -> FundamentalType.StringType
  )

  val emptyInput = TabularData()

  // Test encoding to SQL and back yields the same result.
  private def testReparsable(select: Select): Unit = {
    val selectStatement = select.toStatement
    val anonymousInputs = select.input match {
      case AnonymousInput(td, idx) => Vector.fill(idx + 1)(emptyInput).updated(idx, td)
      case _                       => Vector(emptyInput)
    }

    implicit val context = SqlContext(
      anonymous = anonymousInputs
    )
    withClue(s"Re-Serialized ${selectStatement} should be parseable") {
      val parsed = Select.parse(selectStatement)
      parsed shouldBe Right(select)
    }
  }

  it should "support select *" in {
    val got = SelectBuilder.buildSelect(simpleInput, "SELECT *")
    got shouldBe Right(
      Select(AnonymousInput(simpleInput))
    )
    got.right.get.resultingTabularType shouldBe simpleInput
    testReparsable(got.forceRight)
  }

  it should "support selected from 0" in {
    val got = SelectBuilder.buildSelect(simpleInput, "SELECT * FROM $0")
    got shouldBe Right(
      Select(AnonymousInput(simpleInput))
    )
    got.right.get.resultingTabularType shouldBe simpleInput
    testReparsable(got.forceRight)
  }

  it should "support select from 0 with where" in {
    val got = SelectBuilder.buildSelect(simpleInput, "SELECT * FROM $0 WHERE x = 1")
    got shouldBe Right(
      Select(
        AnonymousInput(simpleInput),
        selection = Vector(
          Condition.Equals(
            ColumnExpression(0, FundamentalType.Int32),
            ConstantExpression(1)
          )
        )
      )
    )
    got.right.get.resultingTabularType shouldBe simpleInput
    testReparsable(got.forceRight)
  }

  it should "support select from with other input" in {
    implicit val context = SqlContext(
      anonymous = Vector(
        simpleInput,
        emptyInput
      )
    )

    val got = SelectBuilder.buildSelect("SELECT * FROM $0")
    got shouldBe Right(
      Select(AnonymousInput(simpleInput))
    )
    got.right.get.resultingTabularType shouldBe simpleInput
    testReparsable(got.forceRight)

    val got2 = SelectBuilder.buildSelect("SELECT 3 FROM $1")
    got2 shouldBe Right(
      Select(AnonymousInput(emptyInput, 1), Some(
        Vector(
          SelectProjection("$1", ConstantExpression(SingleElementBundle(FundamentalType.Int8, Primitive(3: Byte))))
        )
      ))
    )
    testReparsable(got2.forceRight)
  }

  it should "support selecting a single" in {
    val got = SelectBuilder.buildSelect(simpleInput, "SELECT y")
    got shouldBe Right(
      Select(
        AnonymousInput(simpleInput),
        Some(
          Vector(
            SelectProjection("y", ColumnExpression(1, FundamentalType.StringType))
          )
        )
      )
    )
    got.right.get.resultingTabularType shouldBe TabularData(
      "y" -> FundamentalType.StringType
    )
    testReparsable(got.forceRight)
  }

  it should "support selecting multiple" in {
    val got = SelectBuilder.buildSelect(simpleInput, "SELECT y,x")
    got shouldBe Right(
      Select(
        AnonymousInput(simpleInput),
        Some(
          Vector(
            SelectProjection("y", ColumnExpression(1, FundamentalType.StringType)),
            SelectProjection("x", ColumnExpression(0, FundamentalType.Int32))
          )
        )
      )
    )
    got.right.get.resultingTabularType shouldBe TabularData(
      "y" -> FundamentalType.StringType,
      "x" -> FundamentalType.Int32
    )
    testReparsable(got.forceRight)
  }

  it should "support simple casts" in {
    val got = SelectBuilder.buildSelect(simpleInput, "SELECT CAST(x as int64)")
    got shouldBe Right(
      Select(
        AnonymousInput(simpleInput),
        Some(
          Vector(
            SelectProjection(
              "x",
              CastExpression(
                ColumnExpression(0, FundamentalType.Int32),
                FundamentalType.Int64
              )
            )
          )
        )
      )
    )
    got.right.get.resultingTabularType shouldBe TabularData(
      "x" -> FundamentalType.Int64
    )
    testReparsable(got.forceRight)
  }

  it should "support simple constants" in {
    val got = SelectBuilder.buildSelect(emptyInput, "SELECT 1,true,false,2.5,void")
    val expected = Select(
      AnonymousInput(emptyInput),
      Some(
        Vector(
          SelectProjection("$1", ConstantExpression(1: Byte)),
          SelectProjection("$2", ConstantExpression(true)),
          SelectProjection("$3", ConstantExpression(false)),
          SelectProjection("$4", ConstantExpression(2.5f)),
          SelectProjection("$5", ConstantExpression(SingleElementBundle(FundamentalType.VoidType, Primitive.unit)))
        )
      )
    )
    got.right.get.projections.zip(expected.projections).foreach {
      case (a, b) =>
        a shouldBe b
    }
    got shouldBe Right(
      expected
    )
    testReparsable(got.forceRight)
  }

  it should "support simple filters" in {
    val got = SelectBuilder.buildSelect(simpleInput, "SELECT x WHERE x = 5")
    val expected = Select(
      AnonymousInput(simpleInput),
      Some(Vector(
        SelectProjection("x", ColumnExpression(0, FundamentalType.Int32))
      )),
      Vector(
        Condition.Equals(
          ColumnExpression(0, FundamentalType.Int32),
          ConstantExpression(5)
        )
      )
    )
    got shouldBe Right(expected)
    testReparsable(got.forceRight)
  }

  it should "support simple filters II" in {
    val got = SelectBuilder.buildSelect(simpleInput, "SELECT x WHERE y = 'Hello World'")
    val expected = Select(
      AnonymousInput(simpleInput),
      Some(Vector(
        SelectProjection("x", ColumnExpression(0, FundamentalType.Int32))
      )),
      Vector(
        Condition.Equals(
          ColumnExpression(1, FundamentalType.StringType),
          ConstantExpression(Bundle.fundamental("Hello World"))
        )
      )
    )
    got shouldBe Right(expected)
    testReparsable(got.forceRight)
  }

  it should "support simple combined filters" in {
    val got = SelectBuilder.buildSelect(simpleInput, "SELECT x WHERE y = 'Hello World' and x = 1")
    val expected = Select(
      AnonymousInput(simpleInput),
      Some(Vector(
        SelectProjection("x", ColumnExpression(0, FundamentalType.Int32))
      )),
      Vector(
        Condition.Equals(
          ColumnExpression(1, FundamentalType.StringType),
          ConstantExpression(Bundle.fundamental("Hello World"))
        ),
        Condition.Equals(
          ColumnExpression(0, FundamentalType.Int32),
          ConstantExpression(1)
        )
      )
    )
    got shouldBe Right(expected)
    testReparsable(got.forceRight)
  }

  it should "support nullable casts" in {
    val got = SelectBuilder.buildSelect(
      simpleInput,
      "SELECT CAST (x as STRING NULLABLE)"
    ).forceRight
    val expected = Select(
      AnonymousInput(simpleInput),
      Some(
        Vector(
          SelectProjection(
            "x",
            CastExpression(
              ColumnExpression(0, FundamentalType.Int32),
              Nullable(StringType)
            )
          )
        )
      )
    )
    got shouldBe expected
    testReparsable(got)
  }

  it should "support is null checks" in {
    val got = SelectBuilder.buildSelect(
      simpleInput,
      "SELECT y WHERE x IS NULL"
    ).forceRight
    val expected = Select(
      AnonymousInput(simpleInput),
      Some(
        Vector(
          SelectProjection("y", ColumnExpression(1, FundamentalType.StringType))
        )
      ),
      Vector(
        Condition.IsNull(
          ColumnExpression(0, FundamentalType.Int32)
        )
      )
    )
    got shouldBe expected
    testReparsable(got)
  }

  it should "support is not null checks" in {
    val got = SelectBuilder.buildSelect(
      simpleInput,
      "SELECT y WHERE x IS NOT NULL"
    ).forceRight
    val expected = Select(
      AnonymousInput(simpleInput),
      Some(
        Vector(
          SelectProjection("y", ColumnExpression(1, FundamentalType.StringType))
        )
      ),
      Vector(
        Condition.Not(
          Condition.IsNull(
            ColumnExpression(0, FundamentalType.Int32)
          )
        )
      )
    )
    got shouldBe expected
    testReparsable(got)
  }

  it should "support array accesses" in {
    val input = TabularData(
      "x" -> ArrayT(FundamentalType.Int32),
      "y" -> FundamentalType.Int8
    )

    val got = SelectBuilder.buildSelect(
      input,
      "SELECT x[y], size(x)"
    ).forceRight

    got.resultingTabularType shouldBe TabularData(
      "$1" -> Nullable(FundamentalType.Int32),
      "$2" -> FundamentalType.Int32
    )

    val expected = Select(
      AnonymousInput(input),
      Some(
        Vector(
          SelectProjection("$1", ArrayGetExpression(
            ColumnExpression(0, ArrayT(FundamentalType.Int32)),
            CastExpression(
              ColumnExpression(1, FundamentalType.Int8),
              FundamentalType.Int32
            )
          )),
          SelectProjection("$2", SizeExpression(ColumnExpression(0, ArrayT(FundamentalType.Int32))))
        )
      )
    )
    got shouldBe expected
    testReparsable(got)
  }

  it should "support array casts" in {
    val input = TabularData(
      "x" -> ArrayT(FundamentalType.Int32),
      "y" -> FundamentalType.Int8
    )

    val got = SelectBuilder.buildSelect(
      input,
      "SELECT CAST (x as INT32), CAST(y as INT8[][] NULLABLE)"
    ).forceRight

    got.resultingTabularType shouldBe TabularData(
      "x" -> FundamentalType.Int32,
      "y" -> Nullable(ArrayT(ArrayT(FundamentalType.Int8)))
    )

    testReparsable(got)
  }

  it should "support struct accesses" in {
    val input = TabularData(
      "user" -> Struct(
        "name" -> FundamentalType.StringType,
        "age" -> FundamentalType.Int32
      )
    )
    val got = SelectBuilder.buildSelect(
      input,
      "SELECT (user).name, (user).age"
    ).forceRight

    got.resultingTabularType shouldBe TabularData(
      "name" -> FundamentalType.StringType,
      "age" -> FundamentalType.Int32
    )

    testReparsable(got)
  }
}
