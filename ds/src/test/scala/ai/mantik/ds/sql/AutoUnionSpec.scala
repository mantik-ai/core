package ai.mantik.ds.sql

import ai.mantik.ds.element.{Bundle, NullElement, SingleElementBundle}
import ai.mantik.ds.sql.builder.QueryBuilder
import ai.mantik.ds.{FundamentalType, Nullable, TabularData}
import ai.mantik.testutils.TestBase

class AutoUnionSpec extends TestBase {


  val type1 = TabularData(
    "x" -> FundamentalType.Int32,
    "y" -> FundamentalType.StringType
  )

  val type2 = TabularData(
    "x" -> FundamentalType.Int8,
    "z" -> FundamentalType.BoolType
  )
  for {unionAll <- Seq(false, true)} {

    it should s"create simple unions for same type (all=$unionAll)" in {
      AutoUnion.autoUnion(type1, type1, unionAll).forceRight shouldBe Union(
        AnonymousInput(type1, 0),
        AnonymousInput(type1, 1),
        unionAll
      )
    }

    it should s"create automatic adapters (all=$unionAll)" in {
      val got = AutoUnion.autoUnion(type1, type2, unionAll).forceRight
      val expected = Union(
        Select(
          AnonymousInput(type1, 0),
          Some(
            List(
              SelectProjection("x", ColumnExpression(0, FundamentalType.Int32)),
              SelectProjection("y", CastExpression(
                ColumnExpression(1, FundamentalType.StringType), Nullable(FundamentalType.StringType))
              ),
              SelectProjection("z",
                CastExpression(
                  ConstantExpression(Bundle.voidNull),
                  Nullable(FundamentalType.BoolType)
                )
              )
            )
          )),
        Select(
          AnonymousInput(type2, 1),
          Some(
            List(
              SelectProjection("x", CastExpression(
                ColumnExpression(0, FundamentalType.Int8), FundamentalType.Int32)
              ),
              SelectProjection("y",
                CastExpression(
                  ConstantExpression(Bundle.voidNull),
                  Nullable(FundamentalType.StringType)
                )
              ),
              SelectProjection("z", CastExpression(
                ColumnExpression(1, FundamentalType.BoolType), Nullable(FundamentalType.BoolType))
              )
            )
          ),
        ),
        unionAll
      )
      got shouldBe expected
      println(got)
      val asSql = got.toStatement
      implicit val context = SqlContext(
        Vector(type1, type2)
      )
      val parsedBack = QueryBuilder.buildQuery(asSql)
      parsedBack shouldBe Right(expected)
    }
  }
}
