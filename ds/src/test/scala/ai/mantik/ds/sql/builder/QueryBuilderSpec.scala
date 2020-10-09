package ai.mantik.ds.sql.builder

import ai.mantik.ds.element.Bundle
import ai.mantik.ds.operations.BinaryOperation
import ai.mantik.ds.sql.{ AnonymousInput, BinaryExpression, CastExpression, ColumnExpression, ConstantExpression, Query, Select, SelectProjection, SqlContext, Union }
import ai.mantik.ds.sql.parser.AST.AnonymousReference
import ai.mantik.ds.{ FundamentalType, TabularData }
import ai.mantik.testutils.TestBase

class QueryBuilderSpec extends TestBase {

  val simpleInput = TabularData(
    "x" -> FundamentalType.Int32,
    "y" -> FundamentalType.StringType
  )

  val emptyInput = TabularData()

  implicit val context = SqlContext(
    Vector(
      emptyInput,
      emptyInput,
      simpleInput
    )
  )

  private def reparseableTest(query: Query): Unit = {
    val statement = query.toStatement

    withClue(s"Re-Serialized ${statement} should be parseable") {
      val parsed = QueryBuilder.buildQuery(statement)
      parsed shouldBe Right(query)
    }
  }

  it should "parse anonymous input" in {
    val result = QueryBuilder.buildQuery("$2").forceRight
    result shouldBe AnonymousInput(simpleInput, 2)
    reparseableTest(result)
  }

  it should "parse simple unions" in {
    val result = QueryBuilder.buildQuery("$0 UNION $1").forceRight
    result shouldBe Union(
      AnonymousInput(emptyInput, 0),
      AnonymousInput(emptyInput, 1),
      false
    )
    reparseableTest(result)

    val result2 = QueryBuilder.buildQuery("$0 UNION ALL $1").forceRight
    result2 shouldBe Union(
      AnonymousInput(emptyInput, 0),
      AnonymousInput(emptyInput, 1),
      true
    )
    reparseableTest(result2)
  }

  it should "detect type mismatches in unions" in {
    val result = QueryBuilder.buildQuery("$0 UNION $2")
    result.isLeft shouldBe true
    result.left.get should include("Type mismatch")
  }

  it should "parse unions from selects" in {
    val result = QueryBuilder.buildQuery("SELECT 2 from $0 UNION ALL SELECT 3 from $1").forceRight
    result shouldBe Union(
      Select(
        AnonymousInput(emptyInput, 0),
        Some(
          List(
            SelectProjection("$1", ConstantExpression(2: Byte))
          )
        )
      ),
      Select(
        AnonymousInput(emptyInput, 1),
        Some(
          List(
            SelectProjection("$1", ConstantExpression(3: Byte))
          )
        )
      ),
      all = true
    )
    reparseableTest(result)
  }

  it should "multi unions" in {
    val result = QueryBuilder.buildQuery(
      "SELECT 1 FROM $0 UNION SELECT 2 FROM $1 UNION ALL SELECT 3 FROM $1"
    ).forceRight
    result shouldBe Union(
      Union(
        Select(
          AnonymousInput(emptyInput, 0),
          Some(
            List(
              SelectProjection("$1", ConstantExpression(1: Byte))
            )
          )
        ),
        Select(
          AnonymousInput(emptyInput, 1),
          Some(
            List(
              SelectProjection("$1", ConstantExpression(2: Byte))
            )
          )
        ),
        all = false
      ),
      Select(
        AnonymousInput(emptyInput, 1),
        Some(
          List(
            SelectProjection("$1", ConstantExpression(3: Byte))
          )
        )
      ),
      all = true
    )
    reparseableTest(result)
  }

  it should "parse simple selects" in {
    val result = QueryBuilder.buildQuery("SELECT x + 1 as y FROM $2").forceRight
    result shouldBe Select(
      AnonymousInput(simpleInput, 2),
      projections = Some(
        List(
          SelectProjection("y", BinaryExpression(
            BinaryOperation.Add,
            ColumnExpression(0, FundamentalType.Int32),
            // TODO: Can we get rid of this cast ?!
            CastExpression(
              ConstantExpression(1: Byte),
              FundamentalType.Int32
            )
          ))
        )
      )
    )
    reparseableTest(result)
  }

  it should "parse inner selects" in {
    val result = QueryBuilder.buildQuery("SELECT b + 1 as c FROM (SELECT x as b FROM $2)").forceRight
    result shouldBe Select(
      Select(
        AnonymousInput(simpleInput, 2),
        Some(
          List(
            SelectProjection(
              "b",
              ColumnExpression(0, FundamentalType.Int32)
            )
          ))
      ),
      Some(
        List(
          SelectProjection(
            "c",
            BinaryExpression(
              BinaryOperation.Add,
              ColumnExpression(0, FundamentalType.Int32),
              CastExpression(
                ConstantExpression(Bundle.fundamental(1: Byte)),
                FundamentalType.Int32
              )
            )
          )
        )
      )
    )
    reparseableTest(result)
  }
}
