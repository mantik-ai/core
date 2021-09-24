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

import ai.mantik.ds.{FundamentalType, Nullable}
import ai.mantik.ds.element.{Bundle, NullElement, SingleElementBundle}
import ai.mantik.ds.sql.{ConstantExpression, Expression}
import ai.mantik.ds.sql.parser.AST

/** Handles Cast-Relevant conversion Routines. */
private[builder] object ConstantBuilder {

  def convertConstant(constant: AST.ConstantExpressionNode): Either[String, Expression] = {
    constant match {
      case s: AST.StringNode =>
        Right(ConstantExpression(Bundle.fundamental(s.value)))
      case b: AST.BoolNode =>
        Right(ConstantExpression(Bundle.fundamental(b.value)))
      case AST.VoidNode =>
        Right(ConstantExpression(Bundle.void))
      case AST.NullNode =>
        Right(ConstantExpression(SingleElementBundle(Nullable(FundamentalType.VoidType), NullElement)))
      case n: AST.NumberNode =>
        buildBigDecimal(n.value)
    }
  }

  private def buildBigDecimal(bd: BigDecimal): Either[String, Expression] = {
    val bundle = if (bd.isValidByte) {
      Right(Bundle.fundamental(bd.byteValue))
    } else if (bd.isValidInt) {
      Right(Bundle.fundamental(bd.intValue))
    } else if (bd.isValidLong) {
      Right(Bundle.fundamental(bd.longValue))
    } else if (bd.isBinaryFloat) {
      Right(Bundle.fundamental(bd.floatValue))
    } else if (bd.isBinaryDouble) {
      Right(Bundle.fundamental(bd.doubleValue))
    } else {
      Left(s"Could not convert ${bd}")
    }
    bundle.map(ConstantExpression(_))
  }
}
