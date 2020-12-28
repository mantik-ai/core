package ai.mantik.ds.sql.parser

import ai.mantik.ds.{ FundamentalType, ImageChannel }

/** Abstract syntax tree for the parser. */
object AST {

  sealed trait QueryNode

  case class AnonymousReference(id: Int) extends QueryNode

  case class SelectNode(
      selectColumns: Vector[SelectColumnNode] = Vector.empty,
      where: Option[ExpressionNode] = None,
      from: Option[QueryNode] = None
  ) extends QueryNode {
    def isAll: Boolean = selectColumns.isEmpty
  }

  case class UnionNode(
      left: QueryNode,
      right: QueryNode,
      all: Boolean
  ) extends QueryNode

  sealed trait JoinType
  object JoinType {
    case object Inner extends JoinType
    case object Left extends JoinType
    case object Right extends JoinType
    case object Outer extends JoinType
  }

  sealed trait JoinCondition
  object JoinCondition {
    case class On(expression: ExpressionNode) extends JoinCondition
    case class Using(columns: Vector[IdentifierNode]) extends JoinCondition
    case object Cross extends JoinCondition
  }

  case class JoinNode(
      left: QueryNode,
      right: QueryNode,
      joinType: JoinType,
      condition: JoinCondition
  ) extends QueryNode

  case class AliasNode(
      query: QueryNode,
      name: String
  ) extends QueryNode

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

  case object NullNode extends ConstantExpressionNode {
    val value = None
    override def toString: String = "null"
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
  /** Converts something to a tensor (with optional underlying type change). */
  case class TensorTypeNode(underlying: Option[FundamentalType]) extends TypeNode
  /** Converts something to a image (with optional underlying type change). */
  case class ImageTypeNode(underlying: Option[FundamentalType], channel: Option[ImageChannel]) extends TypeNode
  /** Marks something as nullable */
  case class NullableTypeNode(underlying: TypeNode) extends TypeNode
}
