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

import ai.mantik.ds.{FundamentalType, TabularData}
import ai.mantik.ds.sql.{AnonymousInput, ColumnExpression, Select, SelectProjection, SingleQuery, Split, SqlContext}
import ai.mantik.testutils.TestBase

class MultiQueryBuilderSpec extends TestBase {

  val input = TabularData(
    "x" -> FundamentalType.Int32
  )

  implicit val context = SqlContext(
    Vector(
      input
    )
  )

  it should "parse single queries select" in {
    val result = MultiQueryBuilder.buildQuery("SELECT x FROM $0").forceRight
    result shouldBe SingleQuery(
      Select(
        input = AnonymousInput(input),
        projections = Some(
          Vector(
            SelectProjection("x", ColumnExpression(0, FundamentalType.Int32))
          )
        )
      )
    )
  }

  it should "parse a split" in {
    val result = MultiQueryBuilder.buildQuery("SPLIT (SELECT x FROM $0) AT 0.2, 0.4 WITH SHUFFLE 4").forceRight
    result shouldBe Split(
      Select(
        input = AnonymousInput(input),
        projections = Some(
          Vector(
            SelectProjection("x", ColumnExpression(0, FundamentalType.Int32))
          )
        )
      ),
      fractions = Vector(0.2, 0.4),
      shuffleSeed = Some(4)
    )
  }
}
