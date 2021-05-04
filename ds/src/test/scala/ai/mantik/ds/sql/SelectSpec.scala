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

import ai.mantik.ds.element.{
  ArrayElement,
  Bundle,
  NullElement,
  Primitive,
  SomeElement,
  StructElement,
  TabularBundle,
  TensorElement
}
import ai.mantik.ds.sql.run.{Compiler, SelectProgram}
import ai.mantik.ds.{ArrayT, FundamentalType, Nullable, Struct, TabularData, Tensor}
import ai.mantik.testutils.TestBase

class SelectSpec extends TestBase {

  val simpleBundle = TabularBundle
    .build(
      TabularData(
        "x" -> FundamentalType.Int32,
        "y" -> FundamentalType.Int32,
        "z" -> FundamentalType.StringType
      )
    )
    .row(1, 2, "Hello")
    .row(2, 3, "World")
    .row(3, 2, "!!!!")
    .result

  it should "run simple selects" in {
    Select.run(simpleBundle, "select x") shouldBe TabularBundle
      .build(
        TabularData(
          "x" -> FundamentalType.Int32
        )
      )
      .row(1)
      .row(2)
      .row(3)
      .result
  }

  it should "run simple filters" in {
    Select.run(simpleBundle, "select * where y = 2") shouldBe TabularBundle
      .build(
        simpleBundle.model.asInstanceOf[TabularData]
      )
      .row(1, 2, "Hello")
      .row(3, 2, "!!!!")
      .result

    Select.run(simpleBundle, "select * where y = 5").rows.isEmpty shouldBe true
  }

  it should "run complex filters with and" in {
    Select.run(simpleBundle, "select z where y = 2 and x = 3 and z = '!!!!'") shouldBe TabularBundle
      .build(
        TabularData(
          "z" -> FundamentalType.StringType
        )
      )
      .row("!!!!")
      .result
  }

  it should "run complex filters with or" in {
    Select.run(simpleBundle, "select z where y = 2 or x = 3") shouldBe TabularBundle
      .build(
        TabularData(
          "z" -> FundamentalType.StringType
        )
      )
      .row("Hello")
      .row("!!!!")
      .result
  }

  it should "run complex filters with not" in {
    Select.run(simpleBundle, "select z where not(y = 2)") shouldBe TabularBundle
      .build(
        TabularData(
          "z" -> FundamentalType.StringType
        )
      )
      .row("World")
      .result
  }

  it should "run complex filters with not2" in {
    Select.run(simpleBundle, "select z where not(x = 1 or x = 2)") shouldBe TabularBundle
      .build(
        TabularData(
          "z" -> FundamentalType.StringType
        )
      )
      .row("!!!!")
      .result
  }

  it should "run simple calculations" in {
    Select.run(simpleBundle, "select x + y as i") shouldBe TabularBundle
      .build(
        TabularData(
          "i" -> FundamentalType.Int32
        )
      )
      .row(3)
      .row(5)
      .row(5)
      .result

    Select.run(simpleBundle, "select x + y, z") shouldBe TabularBundle
      .build(
        TabularData(
          "$1" -> FundamentalType.Int32,
          "z" -> FundamentalType.StringType
        )
      )
      .row(3, "Hello")
      .row(5, "World")
      .row(5, "!!!!")
      .result
  }

  it should "run simple casts" in {
    Select.run(simpleBundle, "select CAST(x as float64)") shouldBe TabularBundle
      .build(
        TabularData(
          "x" -> FundamentalType.Float64
        )
      )
      .row(1.0)
      .row(2.0)
      .row(3.0)
      .result
  }

  it should "run tensor casts" in {
    Select.run(simpleBundle, "select CAST(x as tensor) where x = 1") shouldBe TabularBundle
      .build(
        TabularData(
          "x" -> Tensor(FundamentalType.Int32, List(1))
        )
      )
      .row(TensorElement(IndexedSeq(1)))
      .result
  }

  it should "run constants" in {
    Select.run(simpleBundle, "select 1") shouldBe TabularBundle
      .build(
        TabularData(
          "$1" -> FundamentalType.Int8
        )
      )
      .row(1.toByte)
      .row(1.toByte)
      .row(1.toByte)
      .result
  }

  val bundleWithNulls = TabularBundle
    .build(
      TabularData(
        "x" -> Nullable(FundamentalType.Int32),
        "y" -> FundamentalType.StringType
      )
    )
    .row(1, "Hello")
    .row(NullElement, "World")
    .result

  it should "allow filtering out null values" in {
    Select.run(
      bundleWithNulls,
      "select * where x is not null"
    ) shouldBe TabularBundle.build(bundleWithNulls.model).row(1, "Hello").result
  }

  it should "allow casting to nullable" in {
    Select.run(
      bundleWithNulls,
      "SELECT x, CAST(y as STRING NULLABLE) as y"
    ) shouldBe TabularBundle
      .build(
        TabularData(
          "x" -> Nullable(FundamentalType.Int32),
          "y" -> Nullable(FundamentalType.StringType)
        )
      )
      .row(1, "Hello")
      .row(NullElement, "World")
      .result
  }

  it should "allow casting from nullable" in {
    Select.run(
      bundleWithNulls,
      "SELECT CAST(x as int32) as x WHERE x IS NOT NULL"
    ) shouldBe TabularBundle
      .build(
        TabularData(
          "x" -> FundamentalType.Int32
        )
      )
      .row(1)
      .result
  }

  it should "run aliases" in {
    val result = Select.run(
      bundleWithNulls,
      "SELECT table.x FROM $0 AS table WHERE x IS NOT NULL"
    )

    result shouldBe TabularBundle
      .build(
        TabularData(
          "x" -> Nullable(FundamentalType.Int32)
        )
      )
      .row(1)
      .result
  }

  it should "run array accesses" in {
    val bundle = TabularBundle
      .build(
        TabularData(
          "x" -> ArrayT(FundamentalType.Int32)
        )
      )
      .row(
        ArrayElement(Primitive(4), Primitive(5), Primitive(6))
      )
      .row(
        ArrayElement()
      )
      .result

    val result = Select.run(
      bundle,
      "SELECT x[2] as a, SIZE(x) as b"
    )

    result shouldBe TabularBundle
      .build(
        TabularData(
          "a" -> Nullable(FundamentalType.Int32),
          "b" -> FundamentalType.Int32
        )
      )
      .row(
        5,
        3
      )
      .row(
        NullElement,
        0
      )
      .result
  }

  it should "correctly handle nullable arrays" in {
    val bundle = TabularBundle
      .build(
        TabularData(
          "x" -> Nullable(ArrayT(FundamentalType.Int32))
        )
      )
      .row(
        NullElement
      )
      .row(
        SomeElement(ArrayElement(Primitive(1)))
      )
      .result

    val result = Select.run(
      bundle,
      "SELECT x[1] as a, SIZE(x) as b, x[null] as c"
    )

    result shouldBe TabularBundle
      .build(
        TabularData(
          "a" -> Nullable(FundamentalType.Int32),
          "b" -> Nullable(FundamentalType.Int32),
          "c" -> Nullable(FundamentalType.Int32)
        )
      )
      .row(
        NullElement,
        NullElement,
        NullElement
      )
      .row(
        SomeElement(Primitive(1)),
        SomeElement(Primitive(1)),
        NullElement
      )
      .result
  }

  it should "correctly handle even more complicaited nullable arrays" in {
    val bundle = TabularBundle
      .build(
        "x" -> ArrayT(Nullable(FundamentalType.Int32)),
        "y" -> Nullable(ArrayT(Nullable(FundamentalType.Int32)))
      )
      .row(
        ArrayElement(NullElement, SomeElement(Primitive(1))),
        NullElement
      )
      .row(
        ArrayElement(SomeElement(Primitive(1)), NullElement),
        SomeElement(ArrayElement(SomeElement(Primitive(1))))
      )
      .result

    val result = Select.run(
      bundle,
      "SELECT x[1] AS a, size(x) AS b, y[1] AS c, size(y) AS d"
    )

    result shouldBe TabularBundle
      .build(
        TabularData(
          "a" -> Nullable(FundamentalType.Int32),
          "b" -> FundamentalType.Int32,
          "c" -> Nullable(FundamentalType.Int32),
          "d" -> Nullable(FundamentalType.Int32)
        )
      )
      .row(
        NullElement,
        Primitive(2),
        NullElement,
        NullElement
      )
      .row(
        SomeElement(Primitive(1)),
        Primitive(2),
        SomeElement(Primitive(1)),
        SomeElement(Primitive(1))
      )
      .result
  }

  it should "run struct acesses" in {
    val bundle = TabularBundle
      .build(
        "x" -> Struct(
          "name" -> FundamentalType.StringType,
          "age" -> Nullable(FundamentalType.Int32)
        )
      )
      .row(StructElement(Primitive("Alice"), SomeElement(Primitive(42))))
      .row(StructElement(Primitive("Bob"), NullElement))
      .result

    val result = Select.run(
      bundle,
      "SELECT (x).name, (x).age"
    )

    result shouldBe TabularBundle
      .build(
        "name" -> FundamentalType.StringType,
        "age" -> Nullable(FundamentalType.Int32)
      )
      .row("Alice", 42)
      .row("Bob", NullElement)
      .result
  }

  it should "work for nullable structs" in {
    val bundle = TabularBundle
      .build(
        "x" -> Nullable(
          Struct(
            "name" -> FundamentalType.StringType,
            "age" -> Nullable(FundamentalType.Int32)
          )
        )
      )
      .row(SomeElement(StructElement(Primitive("Alice"), SomeElement(Primitive(42)))))
      .row(NullElement)
      .result

    val result = Select.run(
      bundle,
      "SELECT (x).name, (x).age"
    )

    result shouldBe TabularBundle
      .build(
        "name" -> Nullable(FundamentalType.StringType),
        "age" -> Nullable(FundamentalType.Int32)
      )
      .row("Alice", 42)
      .row(NullElement, NullElement)
      .result
  }
}
