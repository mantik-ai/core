package ai.mantik.planner.select.parser

import ai.mantik.ds.FundamentalType

/** Abstract syntax tree for the parser. */
object AST {

  case class SelectNode(selectColumns: List[SelectColumnNode] = Nil, where: Option[ExpressionNode] = None) {
    def isAll: Boolean = selectColumns.isEmpty
  }

  case class SelectColumnNode(
      expression: ExpressionNode,
      as: Option[IdentifierNode] = None
  )

  sealed trait ExpressionNode

  sealed trait ConstantExpressionNode extends ExpressionNode {
    val value: Any
    override def toString: String = value.toString
  }
  case class StringNode(value: String) extends ConstantExpressionNode
  case class NumberNode(value: BigDecimal) extends ConstantExpressionNode
  case class BoolNode(value: Boolean) extends ConstantExpressionNode
  case object VoidNode extends ConstantExpressionNode {
    val value = Unit
    override def toString: String = "void"
  }

  case class IdentifierNode(name: String, ignoreCase: Boolean = true) extends ExpressionNode {
    override def toString: String = if (!ignoreCase) {
      "\"" + name + "\""
    } else {
      name
    }
  }
  case class CastNode(expression: ExpressionNode, destinationType: TypeNode) extends ExpressionNode {
    override def toString: String = {
      s"CAST(${expression} as ${destinationType})"
    }
  }

  /**
   * Binary Operation like +,-,and,or.
   * @param operation lowercased operation name
   */
  case class BinaryOperationNode(operation: String, left: ExpressionNode, right: ExpressionNode) extends ExpressionNode {
    override def toString: String = {
      s"(${left} ${operation} ${right})"
    }
  }

  /** An unary operation like "not". */
  case class UnaryOperationNode(operation: String, exp: ExpressionNode) extends ExpressionNode {
    override def toString: String = {
      s"$operation ($exp)"
    }
  }

  /**
   * Type used for casting,
   * Note: we do not support all types, and some are inferred in a later stage
   */
  sealed trait TypeNode
  case class FundamentalTypeNode(ft: FundamentalType) extends TypeNode
  case object TensorTypeNode extends TypeNode
}