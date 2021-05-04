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
package ai.mantik.planner

import ai.mantik.ds.{DataType, FundamentalType, TabularData}
import ai.mantik.ds.element.Bundle
import ai.mantik.elements.{DataSetDefinition, ItemId, MantikHeader, NamedMantikId}
import ai.mantik.planner.impl.TestItems
import ai.mantik.planner.repository.ContentTypes
import ai.mantik.testutils.TestBase

class DataSetSpec extends TestBase {

  val sample = DataSet.literal(
    Bundle.fundamental(100)
  )

  "cached" should "return a cached variant of the DataSet" in {
    val cached = sample.cached
    val cachedSource = cached.payloadSource.asInstanceOf[PayloadSource.Cached]
    cachedSource.source shouldBe sample.payloadSource
    cached.mantikHeader shouldBe sample.mantikHeader
  }

  it should "do nothing, if it is already cached" in {
    val cached = sample.cached
    val cached2 = cached.cached
    cached2 shouldBe cached
  }

  val type1 = TabularData(
    "x" -> FundamentalType.Int32
  )

  val type2 = TabularData(
    "y" -> FundamentalType.Int32
  )

  private def makeDs(dt: DataType): DataSet = {
    // DataSet source is not important here.
    DataSet(
      Source(
        DefinitionSource.Loaded(Some(NamedMantikId("item1234")), ItemId.generate()),
        PayloadSource.Loaded("someId", ContentTypes.ZipFileContentType)
      ),
      MantikHeader.pure(DataSetDefinition(bridge = "someformat", `type` = dt)),
      TestItems.formatBridge
    )
  }

  "autoAdaptOrFail" should "not touch identical datasets" in {
    val ds1 = makeDs(type1)
    ds1.autoAdaptOrFail(type1) shouldBe ds1
  }

  it should "figure out figure out single columns" in {
    val ds1 = makeDs(type1)
    val adapted = ds1.autoAdaptOrFail(type2)
    adapted.dataType shouldBe type2
    adapted.payloadSource shouldBe an[PayloadSource.OperationResult]
  }

  it should "fail if there is no conversion" in {
    val ds1 = makeDs(type1)
    intercept[ConversionNotApplicableException] {
      ds1.autoAdaptOrFail(TabularData("z" -> FundamentalType.BoolType))
    }
  }
}
