package ai.mantik.ds

import ai.mantik.ds.FundamentalType._
import ai.mantik.ds.element.{ ImageElement, Primitive }
import akka.util.ByteString

import scala.collection.immutable.ListMap

object TypeSamples {

  val fundamentalSamples: Seq[(FundamentalType, Primitive[_])] = Seq(
    BoolType -> Primitive(true),
    Int8 -> Primitive(34.toByte),
    Int8 -> Primitive(-1.toByte),
    Uint8 -> Primitive(254.toByte),
    Uint8 -> Primitive(128.toByte),
    Int32 -> Primitive(453583),
    Int32 -> Primitive(-25359234),
    Uint32 -> Primitive(35452343424L.toInt),
    Uint32 -> Primitive(-1),
    Uint32 -> Primitive(363463568),
    Int64 -> Primitive(Long.MaxValue),
    Int64 -> Primitive(Long.MinValue),
    Uint64 -> Primitive(-1.toLong),
    Uint64 -> Primitive(453458430584358L),
    Float32 -> Primitive(1.5f),
    Float32 -> Primitive(-1.5f),
    Float32 -> Primitive(Float.NegativeInfinity),
    Float64 -> Primitive(-1.5),
    Float64 -> Primitive(1.5),
    Float64 -> Primitive(Double.NegativeInfinity),
    StringType -> Primitive("Hello World"),
    VoidType -> Primitive.unit
  )

  val image = (
    Image(2, 3, ListMap(ImageChannel.Black -> ImageComponent(FundamentalType.Uint8))),
    ImageElement(ByteString(1, 2, 3, 4, 5, 6))
  )
}
