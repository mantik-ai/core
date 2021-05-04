/*
 * This file is part of the Mantik Project.
 * Copyright (c) 2020-2021 Mantik UG (Haftungsbeschränkt)
 * Authors: See AUTHORS file
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.
 *
 * Additionally, the following linking exception is granted:
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with other code, such other code is not for that reason
 * alone subject to any of the requirements of the GNU Affero GPL
 * version 3.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license.
 */
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
  for { unionAll <- Seq(false, true) } {

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
            Vector(
              SelectProjection("x", ColumnExpression(0, FundamentalType.Int32)),
              SelectProjection(
                "y",
                CastExpression(ColumnExpression(1, FundamentalType.StringType), Nullable(FundamentalType.StringType))
              ),
              SelectProjection(
                "z",
                CastExpression(
                  ConstantExpression(Bundle.voidNull),
                  Nullable(FundamentalType.BoolType)
                )
              )
            )
          )
        ),
        Select(
          AnonymousInput(type2, 1),
          Some(
            Vector(
              SelectProjection("x", CastExpression(ColumnExpression(0, FundamentalType.Int8), FundamentalType.Int32)),
              SelectProjection(
                "y",
                CastExpression(
                  ConstantExpression(Bundle.voidNull),
                  Nullable(FundamentalType.StringType)
                )
              ),
              SelectProjection(
                "z",
                CastExpression(ColumnExpression(1, FundamentalType.BoolType), Nullable(FundamentalType.BoolType))
              )
            )
          )
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
