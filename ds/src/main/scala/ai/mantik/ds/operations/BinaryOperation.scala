package ai.mantik.ds.operations

import ai.mantik.ds.helper.circe.EnumDiscriminatorCodec

sealed trait BinaryOperation

case object BinaryOperation {
  case object Add extends BinaryOperation
  case object Sub extends BinaryOperation
  case object Mul extends BinaryOperation
  case object Div extends BinaryOperation

  /** JSON Codec for [[BinaryOperation]]. */
  implicit val BinaryOperationCodec = new EnumDiscriminatorCodec[BinaryOperation](
    Seq(
      "add" -> BinaryOperation.Add,
      "sub" -> BinaryOperation.Sub,
      "mul" -> BinaryOperation.Mul,
      "div" -> BinaryOperation.Div
    )
  )
}
