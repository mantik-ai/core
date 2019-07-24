package ai.mantik.ds.formats.binary

import java.nio.ByteOrder

import ai.mantik.ds.{ DataType, FundamentalType, Image, ImageFormat }
import ai.mantik.ds.element.{ Element, ImageElement }
import akka.util.ByteIterator
import ai.mantik.ds.element.PrimitiveEncoder._

/**
 * Some types are "plain" serialized, just written away.
 * This is used in Binary encoding, but also for images in the Natural format.
 * Note: not all formats can be expressed like this (e.g. variable size fields)
 */
@deprecated("Binary decoding should be done by binary bridge.")
object PlainFormat {

  /** Returns the plain size of an element.  */
  def plainSize(dataType: DataType): Option[Int] = {
    dataType match {
      case FundamentalType.Int64    => Some(8)
      case FundamentalType.Uint64   => Some(8)
      case FundamentalType.Uint32   => Some(4)
      case FundamentalType.Int32    => Some(4)
      case FundamentalType.Uint8    => Some(1)
      case FundamentalType.Int8     => Some(1)
      case FundamentalType.BoolType => Some(1)
      case FundamentalType.Float32  => Some(4)
      case FundamentalType.Float64  => Some(8)
      case image: Image if image.format == ImageFormat.Plain =>
        val pixelSize = image.components.values.foldLeft(Some(0): Option[Int]) {
          case (adder, component) =>
            for {
              a <- adder
              p <- plainSize(component.componentType)
            } yield a + p
        }
        pixelSize.map { pixelSize =>
          pixelSize * image.width * image.height
        }
      case somethingElse =>
        // No clear size
        None
    }
  }

  /** Returns a decoder for plain types. */
  def plainDecoder(dataType: DataType)(implicit byteOrder: ByteOrder): Option[ByteIterator => Element] = {
    val fEncoder = fundamentalEncoder

    dataType match {
      case ft: FundamentalType if fEncoder.isDefinedAt(ft) =>
        Some(fEncoder(ft))
      case i: Image if i.format == ImageFormat.Plain =>
        plainSize(i).map { imageByteSize => // TODO: According to doc this could be slow, Ticket #38
        x => {
          ImageElement(x.take(imageByteSize).toByteString)
        }
        }
      case other =>
        None
    }
  }

  private def fundamentalEncoder(implicit byteOrder: ByteOrder): PartialFunction[FundamentalType, ByteIterator => Element] = {
    // We are using the wrap function to make Scala's type system bail out, if we accidently try to use
    // a different type than expected.
    case ft @ FundamentalType.Int64 =>
      x => ft.wrap(x.getLong)
    case ft @ FundamentalType.Int32 =>
      x => ft.wrap(x.getInt)
    case ft @ FundamentalType.Int8 =>
      x => ft.wrap(x.getByte)
    case ft @ FundamentalType.Uint64 =>
      x => ft.wrap(x.getLong)
    case ft @ FundamentalType.Uint32 =>
      x => ft.wrap(x.getInt)
    case ft @ FundamentalType.Uint8 =>
      x => ft.wrap(x.getByte)
    case ft @ FundamentalType.BoolType =>
      x => ft.wrap(x.getByte != 0)
    case ft @ FundamentalType.Float32 =>
      x => ft.wrap(x.getFloat)
    case ft @ FundamentalType.Float64 =>
      x => ft.wrap(x.getDouble)
  }
}
