package ai.mantik.planner.select.builder

import ai.mantik.ds.element.Bundle
import ai.mantik.planner.select.{ ConstantExpression, Expression }
import ai.mantik.planner.select.parser.AST

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
      case n: AST.NumberNode =>
        buildBigDecimal(n.value)
    }
  }

  private def buildBigDecimal(bd: BigDecimal): Either[String, Expression] = {
    val bundle = if (bd.isValidByte) {
      Right(Bundle.fundamental(bd.byteValue()))
    } else if (bd.isValidInt) {
      Right(Bundle.fundamental(bd.intValue()))
    } else if (bd.isValidLong) {
      Right(Bundle.fundamental(bd.longValue()))
    } else if (bd.isBinaryFloat) {
      Right(Bundle.fundamental(bd.floatValue()))
    } else if (bd.isBinaryDouble) {
      Right(Bundle.fundamental(bd.doubleValue()))
    } else {
      Left(s"Could not convert ${bd}")
    }
    bundle.map(ConstantExpression)
  }
}