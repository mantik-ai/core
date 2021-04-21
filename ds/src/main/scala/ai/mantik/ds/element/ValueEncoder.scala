package ai.mantik.ds.element

import ai.mantik.ds.FundamentalType
import FundamentalType._

/**
  * Value encoder provides implicit information about implicit values
  * and can be used to construct primitives of them.
  */
trait ValueEncoder[T] {
  def fundamentalType: FundamentalType
  def wrap(x: T): Primitive[_]
}

object ValueEncoder {

  private def makeValueEncoder[T, ST, FT <: FundamentalType](ft: FT, convert: T => ST) = new ValueEncoder[T] {
    def fundamentalType = ft

    override def wrap(x: T): Primitive[ST] = Primitive(convert(x))
  }

  implicit val byteEncoder = makeValueEncoder[Byte, Byte, Int8.type](Int8, identity)
  implicit val shortEncoder = makeValueEncoder[Short, Int, Int32.type](Int32, _.toInt)
  implicit val charEncoder = makeValueEncoder[Char, Int, Int32.type](Int32, _.toInt)
  implicit val intEncoder = makeValueEncoder[Int, Int, Int32.type](Int32, identity)
  implicit val longEncoder = makeValueEncoder[Long, Long, Int64.type](Int64, identity)
  implicit val floatEncoder = makeValueEncoder[Float, Float, Float32.type](Float32, identity)
  implicit val doubleEncoder = makeValueEncoder[Double, Double, Float64.type](Float64, identity)
  implicit val boolEncoder = makeValueEncoder[Boolean, Boolean, BoolType.type](BoolType, identity)
  implicit val stringEncoder = makeValueEncoder[String, String, StringType.type](StringType, identity)
  implicit val unitEncoder = makeValueEncoder[Unit, Unit, VoidType.type](VoidType, identity)

  def wrap[T: ValueEncoder](x: T): Primitive[_] = {
    implicitly[ValueEncoder[T]].wrap(x)
  }

  def apply[T: ValueEncoder](x: T): Primitive[_] = wrap(x)
}
