package ai.mantik.ds.sql.run

import ai.mantik.ds.{ DataType, FundamentalType }
import ai.mantik.ds.element.{ SingleElementBundle }
import ai.mantik.ds.operations.BinaryOperation

/** A Single OpCode in a stack based interpretation Machine. */
sealed abstract class OpCode(val code: String, val consuming: Int = 1, val producing: Int = 1)

object OpCode {

  val GetCode = "get"
  val ConstantCode = "cnt"
  val PopCode = "pop"
  val CastCode = "cast"
  val NegCode = "neg"
  val EqualsCode = "eq"
  val OrCode = "or"
  val AndCode = "and"
  val ReturnOnFalseCode = "retf"
  val BinaryOpCode = "bn"

  /** Gets an input element and pushes it on to the stack. */
  case class Get(id: Int) extends OpCode(GetCode, consuming = 0)

  /** Push a Constant to the stack. */
  case class Constant(value: SingleElementBundle) extends OpCode(ConstantCode, consuming = 0)

  /** Just pop an element from the stack. */
  case object Pop extends OpCode(PopCode, consuming = 1, producing = 0)

  /**
   * Casts types
   */
  case class Cast(from: DataType, to: DataType) extends OpCode(CastCode)

  // Logic Operations

  /** Negate a boolean expression. */
  case object Neg extends OpCode(NegCode)

  /** Compares two elements, pushes true if equal. */
  case class Equals(dataType: DataType) extends OpCode(EqualsCode, consuming = 2)

  /** Consumes two booleans, returns true if both are true. */
  case object And extends OpCode(AndCode, consuming = 2)

  /** Consumes two booleans, returns true if one of them is true. */
  case object Or extends OpCode(OrCode, consuming = 2)

  // Control Operations
  /** Returns if false is on the stack, does not consume */
  case object ReturnOnFalse extends OpCode(ReturnOnFalseCode, consuming = 0, producing = 0)

  /** Executes a binary operation. */
  case class BinaryOp(dataType: DataType, op: BinaryOperation) extends OpCode(BinaryOpCode, consuming = 2, producing = 1)

}
