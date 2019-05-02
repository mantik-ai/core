package ai.mantik.planner.select.run

import ai.mantik.ds.{ DataType, FundamentalType }
import ai.mantik.ds.element.Element
import ai.mantik.ds.operations.BinaryOperation

/** A Single OpCode in a stack based interpretation Machine. */
sealed abstract class OpCode(val code: String, val consuming: Int = 1, val producing: Int = 1)

object OpCode {

  /** Gets an input element and pushes it on to the stack. */
  case class Get(id: Int) extends OpCode("get", consuming = 0)

  /** Push a Constant to the stack. */
  case class Constant(dataType: DataType, value: Element) extends OpCode("cnt", consuming = 0)

  /** Just pop an element from the stack. */
  case object Pop extends OpCode("pop", consuming = 1, producing = 0)

  /**
   * Casts types
   */
  case class Cast(from: DataType, to: DataType) extends OpCode("cast")

  // Logic Operations

  /** Negate a boolean expression. */
  case object Neg extends OpCode("neg")

  /** Compares two elements, pushes true if equal. */
  case object Equals extends OpCode("eq", consuming = 2)

  /** Consumes two booleans, returns true if both are true. */
  case object And extends OpCode("and", consuming = 2)

  /** Consumes two booleans, returns true if one of them is true. */
  case object Or extends OpCode("or", consuming = 2)

  // Control Operations
  /** Returns if false is on the stack, does not consume */
  case object ReturnOnFalse extends OpCode("retf", consuming = 0, producing = 0)

  // Binary Operation
  case class BinaryOp(ft: FundamentalType, op: BinaryOperation) extends OpCode("binary", consuming = 2, producing = 1)

}
