package ai.mantik.ds.operations

import ai.mantik.ds.{DataType, FundamentalType}
import ai.mantik.ds.element.{Element, Primitive, PrimitiveEncoder}

/** A Binary Operation. */
case class BinaryFunction (
  dataType: DataType,
  op: (Element, Element) => Element
)

object BinaryFunction {

  /** Find a binary for function for the given operation and given data type. */
  def findBinaryFunction(op: BinaryOperation, dataType: DataType): Either[String, BinaryFunction] = {

    def makeResult(op: (Element, Element) => Element) = Right(BinaryFunction(dataType, op))

    binaryFunctions.get(dataType) match {
      case None => Left(s"Could not find binary functions for ${dataType}")
      case Some(x) =>
        op match {
          case BinaryOperation.Add => makeResult(x.add)
          case BinaryOperation.Sub => makeResult(x.sub)
          case BinaryOperation.Mul => makeResult(x.mul)
          case BinaryOperation.Div => makeResult(x.div)
        }
    }
  }

  private val binaryFunctions: Map[DataType, BinaryFunctions] = Seq(
    makeBinaryFunction[FundamentalType.Int8.type, Byte](FundamentalType.Int8)(
      (a,b) => (a + b).toByte,
      (a,b) => (a - b).toByte,
      (a,b) => (a * b).toByte,
      (a,b) => (a / b).toByte
    ),
    makeBinaryFunction[FundamentalType.Uint8.type, Byte](FundamentalType.Uint8)(
      (a,b) => (a + b).toByte,
      (a,b) => (a - b).toByte,
      (a,b) => (a * b).toByte,
      (a,b) => (java.lang.Byte.toUnsignedInt(a) / java.lang.Byte.toUnsignedInt(b)).toByte
    ),
    makeBinaryFunction[FundamentalType.Int32.type, Int](FundamentalType.Int32)(
      (a,b) => a + b,
      (a,b) => a - b,
      (a,b) => a * b,
      (a,b) => a / b
    ),
    makeBinaryFunction[FundamentalType.Uint32.type, Int](FundamentalType.Uint32)(
      (a,b) => a + b,
      (a,b) => a - b,
      (a,b) => a * b,
      (a,b) => java.lang.Integer.divideUnsigned(a, b)
    ),
    makeBinaryFunction[FundamentalType.Int64.type, Long](FundamentalType.Int64)(
      (a,b) => a + b,
      (a,b) => a - b,
      (a,b) => a * b,
      (a,b) => a / b
    ),
    makeBinaryFunction[FundamentalType.Uint64.type, Long](FundamentalType.Uint64)(
      (a,b) => a + b,
      (a,b) => a - b,
      (a,b) => a * b,
      (a,b) => java.lang.Long.divideUnsigned(a,b)
    ),
    makeBinaryFunction[FundamentalType.Float32.type, Float](FundamentalType.Float32)(
      (a,b) => a + b,
      (a,b) => a - b,
      (a,b) => a * b,
      (a,b) => a / b
    ),
    makeBinaryFunction[FundamentalType.Float64.type, Double](FundamentalType.Float64)(
      (a,b) => a + b,
      (a,b) => a - b,
      (a,b) => a * b,
      (a,b) => a / b
    )
  ).map(x => x.ft -> x).toMap

  private case class BinaryFunctions(
    ft: FundamentalType,
    add: (Element, Element) => Element,
    sub: (Element, Element) => Element,
    mul: (Element, Element) => Element,
    div: (Element, Element) => Element,
  )

  private def makeBinaryFunction[FT <: FundamentalType, ST](ft: FT)(
    add: (ST, ST) => ST,
    sub: (ST, ST) => ST,
    mul: (ST, ST) => ST,
    div: (ST, ST) => ST
  )(implicit aux: PrimitiveEncoder.Aux[FT, ST]): BinaryFunctions = {
    BinaryFunctions(
      ft,
      add = wrapFunction(add),
      sub = wrapFunction(sub),
      mul = wrapFunction(mul),
      div = wrapFunction(div)
    )
  }

  private def wrapFunction[FT <: FundamentalType, ST](op: (ST, ST) => ST)(implicit aux: PrimitiveEncoder.Aux[FT, ST]): (Element, Element) => Element = {
    (a,b) => aux.wrap(
      op(
        aux.unwrap(a.asInstanceOf[Primitive[_]]),
        aux.unwrap(b.asInstanceOf[Primitive[_]])
      )
    )
  }
}