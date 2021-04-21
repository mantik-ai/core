package ai.mantik.ds.element

import ai.mantik.ds.FundamentalType
import ai.mantik.ds.FundamentalType._

import scala.util.Try

/**
  * Type class which wraps fundamental types into Primitive instances.
  * The purpose is to have a consistent mapping and to hide all these
  * type erasure beyond a layer of type class magic.
  */
trait PrimitiveEncoder[T <: FundamentalType] {
  type ScalaType

  def wrap(s: ScalaType): Primitive[ScalaType]

  def ordering: Ordering[ScalaType]

  def unwrap(p: Primitive[_]): ScalaType = p.asInstanceOf[Primitive[ScalaType]].x

  /** Try to auto convert into the matching type. */
  val convert: PartialFunction[Any, ScalaType]
}

object PrimitiveEncoder {

  // Aux Pattern, see http://gigiigig.github.io/posts/2015/09/13/aux-pattern.html
  type Aux[A0 <: FundamentalType, B0] = PrimitiveEncoder[A0] {
    type ScalaType = B0
  }

  private def makePrimitiveEncoder[T <: FundamentalType, ST](
      convertPf: PartialFunction[Any, ST]
  )(implicit ord: Ordering[ST]) = new PrimitiveEncoder[T] {
    override final type ScalaType = ST

    override def wrap(s: ScalaType): Primitive[ScalaType] = Primitive(s)

    override def ordering: Ordering[ST] = ord

    val convert = convertPf
  }

  implicit val int32Encoder = makePrimitiveEncoder[Int32.type, Int] {
    case b: Byte                             => b
    case s: Short                            => s
    case i: Int                              => i
    case s: String if Try(s.toInt).isSuccess => s.toInt
  }

  implicit val int64Encoder = makePrimitiveEncoder[Int64.type, Long] {
    case b: Byte                              => b
    case s: Short                             => s
    case i: Int                               => i
    case l: Long                              => l
    case s: String if Try(s.toLong).isSuccess => s.toLong
  }

  implicit val int8Encoder = makePrimitiveEncoder[Int8.type, Byte] {
    case b: Byte                              => b
    case s: String if Try(s.toByte).isSuccess => s.toByte
  }

  implicit val uint64Encoder = makePrimitiveEncoder[Uint64.type, Long] {
    case b: Byte if b >= 0                    => b
    case s: Short if s >= 0                   => s
    case i: Int if i >= 0                     => i
    case l: Long                              => l // no < 0 check here, as we encode uint64
    case s: String if Try(s.toLong).isSuccess => s.toLong
  }
  implicit val uint32Encoder = makePrimitiveEncoder[Uint32.type, Int] {
    case b: Byte if b >= 0                   => b
    case s: Short if s >= 0                  => s
    case i: Int                              => i // no < 0 check here, as we encode uint32
    case s: String if Try(s.toInt).isSuccess => s.toInt
  }
  implicit val uint8Encoder = makePrimitiveEncoder[Uint8.type, Byte] {
    case b: Byte                              => b
    case s: String if Try(s.toByte).isSuccess => s.toByte
  }

  implicit val boolEncoder = makePrimitiveEncoder[BoolType.type, Boolean] {
    case b: Boolean                              => b
    case s: String if Try(s.toBoolean).isSuccess => s.toBoolean
  }

  implicit val stringEncoder = makePrimitiveEncoder[StringType.type, String] { case s: String =>
    s
  }

  implicit val float32Encoder = makePrimitiveEncoder[Float32.type, Float] {
    case f: Float                              => f
    case s: String if Try(s.toFloat).isSuccess => s.toFloat
  }

  implicit val float64Encoder = makePrimitiveEncoder[Float64.type, Double] {
    case f: Float                               => f
    case d: Double                              => d
    case s: String if Try(s.toDouble).isSuccess => s.toDouble
  }

  implicit val voidEncoder = makePrimitiveEncoder[VoidType.type, Unit] { case _ =>
    ()
  }

  implicit class PrimitiveExtensions[T <: FundamentalType, ST](t: T)(implicit f: Aux[T, ST]) {
    def wrap(s: ST): Primitive[ST] = {
      f.wrap(s)
    }
  }

  /** Lookup a fundamental type. */
  def lookup(fundamentalType: FundamentalType): PrimitiveEncoder[_ <: FundamentalType] = {
    fundamentalType match {
      case BoolType   => boolEncoder
      case Int8       => int8Encoder
      case Int32      => int32Encoder
      case Int64      => int64Encoder
      case Uint8      => uint8Encoder
      case Uint32     => uint32Encoder
      case Uint64     => uint64Encoder
      case Float32    => float32Encoder
      case Float64    => float64Encoder
      case StringType => stringEncoder
      case VoidType   => voidEncoder
    }
  }
}
