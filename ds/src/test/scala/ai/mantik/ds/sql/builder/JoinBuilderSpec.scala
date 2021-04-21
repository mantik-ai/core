package ai.mantik.ds.sql.builder

import ai.mantik.ds.Errors.FeatureNotSupported
import ai.mantik.ds.operations.BinaryOperation
import ai.mantik.ds.sql.JoinCondition.UsingColumn
import ai.mantik.ds.sql._
import ai.mantik.ds.{FundamentalType, Nullable, TabularData}
import ai.mantik.testutils.TestBase

class JoinBuilderSpec extends TestBase {

  "innerTabularData" should "work" in {
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

    JoinBuilder.innerTabularData(left, right, JoinType.Inner) shouldBe QueryTabularType(
      "x" -> FundamentalType.Int32,
      "y" -> FundamentalType.StringType,
      "x0" -> FundamentalType.Int64,
      "z" -> Nullable(FundamentalType.StringType)
    )

    JoinBuilder.innerTabularData(left, right, JoinType.Left) shouldBe QueryTabularType(
      "x" -> FundamentalType.Int32,
      "y" -> FundamentalType.StringType,
      "x0" -> Nullable(FundamentalType.Int64),
      "z" -> Nullable(FundamentalType.StringType)
    )

    JoinBuilder.innerTabularData(left, right, JoinType.Right) shouldBe QueryTabularType(
      "x0" -> Nullable(FundamentalType.Int32),
      "y" -> Nullable(FundamentalType.StringType),
      "x" -> FundamentalType.Int64,
      "z" -> Nullable(FundamentalType.StringType)
    )

    JoinBuilder.innerTabularData(left, right, JoinType.Outer) shouldBe QueryTabularType(
      "x" -> Nullable(FundamentalType.Int32),
      "y" -> Nullable(FundamentalType.StringType),
      "x0" -> Nullable(FundamentalType.Int64),
      "z" -> Nullable(FundamentalType.StringType)
    )
  }

  it should "crash on attack types" in {
    def makeAttackType(size: Int): TabularData = {
      TabularData(("n" -> FundamentalType.Int32) +: (for (i <- 1 until size) yield {
        s"n${i}" -> FundamentalType.Int32
      }): _*)
    }
    val attack1 = makeAttackType(100)
    val result = JoinBuilder.innerTabularData(AnonymousInput(attack1), AnonymousInput(attack1), JoinType.Inner)
    result.columns.size shouldBe 200

    val attack2 = makeAttackType(200)
    intercept[FeatureNotSupported] {
      JoinBuilder.innerTabularData(AnonymousInput(attack2), AnonymousInput(attack2), JoinType.Inner)
    }
  }

  val tabular1 = TabularData(
    "x" -> FundamentalType.Int32,
    "y" -> FundamentalType.StringType
  )

  val tabular2 = TabularData(
    "w" -> FundamentalType.BoolType,
    "x" -> FundamentalType.Int32,
    "z" -> FundamentalType.Float32
  )

  val tabular3 = TabularData(
    "x" -> FundamentalType.Int32,
    "y" -> FundamentalType.StringType,
    "z" -> FundamentalType.Float32
  )

  val tabular4 = TabularData(
    "x" -> FundamentalType.Int64,
    "z" -> FundamentalType.StringType
  )

  def joinTest(sql: String, doc: String)(expected: Join) = {
    implicit val context = SqlContext(Vector(tabular1, tabular2, tabular3, tabular4))

    it should s"handle ${sql} ($doc)" in {
      val parsed = QueryBuilder.buildQuery(sql).forceRight
      parsed shouldBe expected
      val serializedAgain = SqlFormatter.formatSql(parsed)
      println(s"Original:     ${sql}")
      println(s"Reserialized: ${serializedAgain} ")
      val parsedAgain = QueryBuilder.buildQuery(serializedAgain)
      parsedAgain shouldBe Right(parsed)
    }

  }

  // Cartesian join
  joinTest("$0 JOIN $1 ON true", "cartesian") {
    Join(
      AnonymousInput(tabular1, 0),
      AnonymousInput(tabular2, 1),
      JoinType.Inner,
      JoinCondition.On(ConstantExpression(true).asCondition.get)
    )
  }

  joinTest("$0 JOIN $1 USING x", "simple Using") {
    Join(
      AnonymousInput(tabular1, 0),
      AnonymousInput(tabular2, 1),
      JoinType.Inner,
      JoinCondition.Using(Vector(UsingColumn("x", false, 0, 1, 3, FundamentalType.Int32)))
    )
  }

  joinTest("$0 JOIN $2 USING x,y", "simple Using with multiple arguments") {
    Join(
      AnonymousInput(tabular1, 0),
      AnonymousInput(tabular3, 2),
      JoinType.Inner,
      JoinCondition.Using(
        Vector(
          UsingColumn("x", leftId = 0, rightId = 0, dropId = 2, dataType = FundamentalType.Int32),
          UsingColumn("y", leftId = 1, rightId = 1, dropId = 3, dataType = FundamentalType.StringType)
        )
      )
    )
  }

  joinTest("$0 JOIN $3 USING x", "using with type adaption") {
    Join(
      AnonymousInput(tabular1, 0),
      AnonymousInput(tabular4, 3),
      JoinType.Inner,
      JoinCondition.Using(
        Vector(UsingColumn("x", leftId = 0, rightId = 0, dropId = 2, dataType = FundamentalType.Int64))
      )
    )
  }

  joinTest("$0 JOIN $1 ON x = z", "on with cast") {
    Join(
      AnonymousInput(tabular1, 0),
      AnonymousInput(tabular2, 1),
      JoinType.Inner,
      JoinCondition.On(
        Condition.Equals(
          CastExpression(
            ColumnExpression(0, FundamentalType.Int32),
            FundamentalType.Float64
          ),
          CastExpression(
            ColumnExpression(4, FundamentalType.Float32),
            FundamentalType.Float64
          )
        )
      )
    )
  }

  /*
  // TODO: No Support yet for > comparison
  joinTest("$0 JOIN $1 ON x > z", "on with comparison") {
    Join(
      AnonymousInput(tabular1, 0),
      AnonymousInput(tabular2, 1),
      JoinType.Inner,
      filter = List()
    )
  }
   */

  joinTest("$0 JOIN $1 ON x + 1 = x", "on with filter") {
    Join(
      AnonymousInput(tabular1, 0),
      AnonymousInput(tabular2, 1),
      JoinType.Inner,
      JoinCondition.On(
        Condition.Equals(
          BinaryOperationExpression(
            BinaryOperation.Add,
            ColumnExpression(0, FundamentalType.Int32),
            ConstantExpression(1)
          ),
          ColumnExpression(0, FundamentalType.Int32)
        )
      )
    )
  }

  joinTest("$0 JOIN $1 ON x + 1 = x AND x = z", "on with filter and comparison") {
    Join(
      AnonymousInput(tabular1, 0),
      AnonymousInput(tabular2, 1),
      JoinType.Inner,
      JoinCondition.On(
        Condition.And(
          Condition.Equals(
            BinaryOperationExpression(
              BinaryOperation.Add,
              ColumnExpression(0, FundamentalType.Int32),
              ConstantExpression(1)
            ),
            ColumnExpression(0, FundamentalType.Int32)
          ),
          Condition.Equals(
            CastExpression(
              ColumnExpression(0, FundamentalType.Int32),
              FundamentalType.Float64
            ),
            CastExpression(
              ColumnExpression(4, FundamentalType.Float32),
              FundamentalType.Float64
            )
          )
        )
      )
    )
  }

  for {
    joinType <- Seq(
      JoinType.Inner,
      JoinType.Left,
      JoinType.Right,
      JoinType.Outer
    )
  } joinTest(s"$$0 ${joinType.sqlName} JOIN $$1 USING x", s"join type ${joinType.sqlName}") {
    val dropId = if (joinType == JoinType.Right) {
      0
    } else {
      3
    }
    Join(
      AnonymousInput(tabular1, 0),
      AnonymousInput(tabular2, 1),
      joinType,
      JoinCondition.Using(
        Vector(UsingColumn("x", leftId = 0, rightId = 1, dropId = dropId, dataType = FundamentalType.Int32))
      )
    )
  }

  joinTest("$0 JOIN $1 USING \"x\"", "Join with escaped USING column") {
    Join(
      AnonymousInput(tabular1, 0),
      AnonymousInput(tabular2, 1),
      JoinType.Inner,
      JoinCondition.Using(
        Vector(UsingColumn("x", true, leftId = 0, rightId = 1, dropId = 3, dataType = FundamentalType.Int32))
      )
    )
  }
}
