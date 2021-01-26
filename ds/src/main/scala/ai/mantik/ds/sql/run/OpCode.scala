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
  val IsNullCode = "isn"
  val UnpackNullableJumpCode = "unj"
  val PackNullableCode = "pn"
  val ArraySizeCode = "arraysize"
  val ArrayGetCode = "arrayget"
  val StructGetCode = "structget"

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

  /** Consumes one value and returns true if it was null */
  case object IsNull extends OpCode(IsNullCode, consuming = 1)

  // Control Operations
  /** Returns if false is on the stack, does not consume */
  case object ReturnOnFalse extends OpCode(ReturnOnFalseCode, consuming = 0, producing = 0)

  /** Executes a binary operation. */
  case class BinaryOp(dataType: DataType, op: BinaryOperation) extends OpCode(BinaryOpCode, consuming = 2, producing = 1)

  /**
   * Unpack a nullable value, if it's a null then emits a null and jump for a given offset
   * @param drop if not null, then additional n elements are dropped from the stack
   */
  case class UnpackNullableJump(offset: Int, drop: Int = 0) extends OpCode(UnpackNullableJumpCode, consuming = 1 + drop)
  /**
   * Wraps a value into a Nullable.
   * (Note: some platforms may do nothing here, if they dont distinguish between Nullable and Value itself.
   */
  case object PackNullable extends OpCode(PackNullableCode)

  case object ArrayGet extends OpCode(ArrayGetCode, consuming = 2)
  case object ArraySize extends OpCode(ArraySizeCode)

  /** Fetch an element from a struct. */
  case class StructGet(idx: Int) extends OpCode(StructGetCode)
}
