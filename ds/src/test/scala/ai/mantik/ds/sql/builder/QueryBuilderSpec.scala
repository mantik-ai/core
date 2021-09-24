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
package ai.mantik.ds.sql.builder

import ai.mantik.ds.element.Bundle
import ai.mantik.ds.operations.BinaryOperation
import ai.mantik.ds.sql.JoinCondition.UsingColumn
import ai.mantik.ds.sql.{
  Alias,
  AnonymousInput,
  BinaryOperationExpression,
  CastExpression,
  ColumnExpression,
  Condition,
  ConstantExpression,
  Join,
  JoinCondition,
  JoinType,
  Query,
  Select,
  SelectProjection,
  SqlContext,
  Union
}
import ai.mantik.ds.{FundamentalType, TabularData}
import ai.mantik.testutils.TestBase

class QueryBuilderSpec extends TestBase {

  val simpleInput = TabularData(
    "x" -> FundamentalType.Int32,
    "y" -> FundamentalType.StringType
  )

  val otherInput = TabularData(
    "x" -> FundamentalType.Int32,
    "z" -> FundamentalType.StringType
  )

  val emptyInput = TabularData()

  implicit val context = SqlContext(
    Vector(
      emptyInput,
      emptyInput,
      simpleInput,
      otherInput
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
    result.forceLeft should include("Type mismatch")
  }

  it should "parse unions from selects" in {
    val result = QueryBuilder.buildQuery("SELECT 2 from $0 UNION ALL SELECT 3 from $1").forceRight
    result shouldBe Union(
      Select(
        AnonymousInput(emptyInput, 0),
        Some(
          Vector(
            SelectProjection("$1", ConstantExpression(2: Byte))
          )
        )
      ),
      Select(
        AnonymousInput(emptyInput, 1),
        Some(
          Vector(
            SelectProjection("$1", ConstantExpression(3: Byte))
          )
        )
      ),
      all = true
    )
    reparseableTest(result)
  }

  it should "multi unions" in {
    val result = QueryBuilder
      .buildQuery(
        "SELECT 1 FROM $0 UNION SELECT 2 FROM $1 UNION ALL SELECT 3 FROM $1"
      )
      .forceRight
    result shouldBe Union(
      Union(
        Select(
          AnonymousInput(emptyInput, 0),
          Some(
            Vector(
              SelectProjection("$1", ConstantExpression(1: Byte))
            )
          )
        ),
        Select(
          AnonymousInput(emptyInput, 1),
          Some(
            Vector(
              SelectProjection("$1", ConstantExpression(2: Byte))
            )
          )
        ),
        all = false
      ),
      Select(
        AnonymousInput(emptyInput, 1),
        Some(
          Vector(
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
        Vector(
          SelectProjection(
            "y",
            BinaryOperationExpression(
              BinaryOperation.Add,
              ColumnExpression(0, FundamentalType.Int32),
              ConstantExpression(1)
            )
          )
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
          Vector(
            SelectProjection(
              "b",
              ColumnExpression(0, FundamentalType.Int32)
            )
          )
        )
      ),
      Some(
        Vector(
          SelectProjection(
            "c",
            BinaryOperationExpression(
              BinaryOperation.Add,
              ColumnExpression(0, FundamentalType.Int32),
              ConstantExpression(1)
            )
          )
        )
      )
    )
    reparseableTest(result)
  }

  it should "parse simple joins" in {
    val result = QueryBuilder.buildQuery("SELECT * FROM $2 LEFT JOIN $3 USING x").forceRight
    result shouldBe Select(
      Join(
        AnonymousInput(simpleInput, 2),
        AnonymousInput(otherInput, 3),
        JoinType.Left,
        JoinCondition.Using(
          Vector(UsingColumn("x", leftId = 0, rightId = 0, dropId = 2, dataType = FundamentalType.Int32))
        )
      )
    )
    reparseableTest(result)
  }

  it should "parse simple joins with aliases" in {
    val result = QueryBuilder.buildQuery("SELECT l.x, r.z FROM $2 AS l JOIN $3 AS r ON l.x = r.x").forceRight
    result shouldBe Select(
      Join(
        Alias(AnonymousInput(simpleInput, 2), "l"),
        Alias(AnonymousInput(otherInput, 3), "r"),
        JoinType.Inner,
        JoinCondition.On(
          Condition.Equals(
            ColumnExpression(0, FundamentalType.Int32),
            ColumnExpression(2, FundamentalType.Int32)
          )
        )
      ),
      Some(
        Vector(
          SelectProjection("l.x", ColumnExpression(0, FundamentalType.Int32)),
          SelectProjection("r.z", ColumnExpression(3, FundamentalType.StringType))
        )
      )
    )
    reparseableTest(result)
    result.resultingTabularType shouldBe TabularData(
      "x" -> FundamentalType.Int32,
      "z" -> FundamentalType.StringType
    )
  }
}
