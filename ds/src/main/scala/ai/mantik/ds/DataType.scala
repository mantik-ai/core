package ai.mantik.ds

import io.circe.{ Decoder, Encoder }

import scala.collection.immutable.ListMap

// Note: this is just a rough draft and subject of heavy changes.

// TODO: While ListMap preserve the order (which is good for us) they have bad performance characteristics (Ticket #38)!

/** Describes a single Datatype. */
sealed trait DataType

object DataType {
  implicit lazy val encoder: Encoder[DataType] = DataTypeJsonAdapter.typeEncoder
  implicit lazy val decoder: Decoder[DataType] = DataTypeJsonAdapter.typeDecoder
}

/** Tabular data, usually the root of the data. */
case class TabularData(
    columns: ListMap[String, DataType],
    rowCount: Option[Long] = None
) extends DataType {

  /** Returns the index of a column with a given name. */
  def lookupColumnIndex(name: String): Option[Int] = {
    val it = columns.iterator
    it.indexWhere(_._1 == name) match {
      case -1 => None
      case n  => Some(n)
    }
  }

}

object TabularData {
  /** Build a tabular data from a name type list. */
  def apply(columns: (String, DataType)*): TabularData = TabularData(ListMap(columns: _*), None)
}

/** Describes a fundamental type. */
sealed trait FundamentalType extends DataType {
  /** Returns the simple string name of the fundamental type. */
  def name: String = DataTypeJsonAdapter.fundamentalTypeToName(this)

  override def toString: String = name
}

object FundamentalType {
  sealed trait IntegerType extends FundamentalType {
    def bits: Int
  }

  sealed abstract class SignedInteger(val bits: Int) extends IntegerType
  sealed abstract class UnsignedInteger(val bits: Int) extends IntegerType

  case object Int8 extends SignedInteger(8)
  case object Int32 extends SignedInteger(32)
  case object Int64 extends SignedInteger(64)
  case object Uint8 extends UnsignedInteger(8)
  case object Uint32 extends UnsignedInteger(32)
  case object Uint64 extends UnsignedInteger(64)

  sealed abstract class FloatingPoint(val bits: Int) extends FundamentalType
  case object Float32 extends FloatingPoint(32)
  case object Float64 extends FloatingPoint(64)

  case object StringType extends FundamentalType

  case object BoolType extends FundamentalType

  case object VoidType extends FundamentalType

  /** Parses a fundamental type from Name. */
  def fromName(name: String): FundamentalType = DataTypeJsonAdapter.fundamentalTypeFromName(name)
}

sealed trait ImageChannel

object ImageChannel {
  case object Red extends ImageChannel
  case object Blue extends ImageChannel
  case object Green extends ImageChannel
  case object Black extends ImageChannel
}

/** Describe a single image component */
case class ImageComponent(
    componentType: FundamentalType
)

sealed trait ImageFormat

object ImageFormat {
  /** Image is plain encoded (bytes directly after each other, row after row). */
  case object Plain extends ImageFormat
  /** Image is in PNG Format. */
  case object Png extends ImageFormat
}

/** DataType for images. */
case class Image(
    width: Int,
    height: Int,
    components: ListMap[ImageChannel, ImageComponent],
    format: ImageFormat = ImageFormat.Plain
) extends DataType {

  private def channelsToString: String = {
    components.map {
      case (channel, component) =>
        channel.toString + ": " + component.componentType.toString
    }.mkString(",")
  }

  override def toString: String = s"Image(${width}x${height}, [${channelsToString}])"
}

/**
 * Data Type for Tensors
 * @param componentType underlying fundamental type (dtype in TensorFlow).
 * @param shape shape of the Tensor. No support for varying shape yet.
 */
case class Tensor(
    componentType: FundamentalType,
    shape: Seq[Int]
) extends DataType {
  require(shape.nonEmpty, "Shape may not be empty")
  require(shape.forall(_ > 0), "All shape elements must be > 0")

  /** Returns the element count a packed tensor would have. */
  lazy val packedElementCount: Long = shape.foldLeft(1L)(_ * _.toLong)
}

