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
package ai.mantik.ds.operations

import ai.mantik.ds.element.Primitive
import ai.mantik.ds.{FundamentalType, TypeSamples}
import ai.mantik.testutils.TestBase

class BinaryFunctionSpec extends TestBase {

  it should "add and subtract for all types" in {
    for {
      (sampleType, value) <- TypeSamples.fundamentalSamples
      if sampleType.isInstanceOf[FundamentalType.IntegerType] || sampleType.isInstanceOf[FundamentalType.FloatingPoint]
      if !isSpecialFloat(value)
    } {
      val add = BinaryFunction.findBinaryFunction(BinaryOperation.Add, sampleType).right.getOrElse(fail())
      val sub = BinaryFunction.findBinaryFunction(BinaryOperation.Sub, sampleType).right.getOrElse(fail())
      add.dataType shouldBe sampleType
      sub.dataType shouldBe sampleType

      val result = add.op(value, value)
      result shouldNot be(value)

      val back = sub.op(result, value)
      back shouldBe value
    }
  }

  it should "multiply and divide for all types" in {
    for {
      (sampleType, value) <- TypeSamples.fundamentalSamples
      if sampleType.isInstanceOf[FundamentalType.IntegerType] || sampleType.isInstanceOf[FundamentalType.FloatingPoint]
      if !isSpecialFloat(value)
    } {
      val mul = BinaryFunction.findBinaryFunction(BinaryOperation.Mul, sampleType).right.getOrElse(fail())
      val div = BinaryFunction.findBinaryFunction(BinaryOperation.Div, sampleType).right.getOrElse(fail())
      mul.dataType shouldBe sampleType
      div.dataType shouldBe sampleType

      val result = mul.op(value, value)
      result shouldNot be(value)

      val back = div.op(result, value)
      // Due integer divide it can happen that back is not the same as value
      val multiplyAgain = mul.op(back, value)
      val divideAgain = div.op(multiplyAgain, value)
      divideAgain shouldBe back
    }
  }

  it should "have handling for unsigned types for 8bit" in {
    val signedDiv = BinaryFunction.findBinaryFunction(BinaryOperation.Div, FundamentalType.Int8).right.getOrElse(fail)
    val unsigendDiv =
      BinaryFunction.findBinaryFunction(BinaryOperation.Div, FundamentalType.Uint8).right.getOrElse(fail)
    signedDiv.op(Primitive(-1.toByte), Primitive(5.toByte)) shouldBe Primitive(0.toByte)
    unsigendDiv.op(Primitive(-1.toByte), Primitive(5.toByte)) shouldBe Primitive(51.toByte)
  }

  it should "have handling for unsigned types for 32bit" in {
    val signedDiv = BinaryFunction.findBinaryFunction(BinaryOperation.Div, FundamentalType.Int32).right.getOrElse(fail)
    val unsigendDiv =
      BinaryFunction.findBinaryFunction(BinaryOperation.Div, FundamentalType.Uint32).right.getOrElse(fail)
    signedDiv.op(Primitive(-1), Primitive(5)) shouldBe Primitive(0)
    unsigendDiv.op(Primitive(-1), Primitive(5)) shouldBe Primitive(858993459)
  }

  it should "have handling for unsigned types for 64bit" in {
    val signedDiv = BinaryFunction.findBinaryFunction(BinaryOperation.Div, FundamentalType.Int64).right.getOrElse(fail)
    val unsigendDiv =
      BinaryFunction.findBinaryFunction(BinaryOperation.Div, FundamentalType.Uint64).right.getOrElse(fail)
    signedDiv.op(Primitive(-1L), Primitive(5L)) shouldBe Primitive(0)
    unsigendDiv.op(Primitive(-1L), Primitive(5L)) shouldBe Primitive(3689348814741910323L)
  }

  private def isSpecialFloat(value: Primitive[_]): Boolean = {
    value.x == Float.NegativeInfinity || value.x == Float.PositiveInfinity || value.x == Float.NaN ||
    value.x == Double.NegativeInfinity || value.x == Double.PositiveInfinity || value.x == Double.NaN
  }
}
