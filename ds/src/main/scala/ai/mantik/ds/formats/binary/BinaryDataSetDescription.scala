package ai.mantik.ds.formats.binary

import ai.mantik.ds.DataType
import io.circe.{ Decoder, ObjectEncoder }

/**
 * Describes the content of (multiple) binary files which form together form tabellaric data.
 *
 * This is a JVM Implementation of the `binary` Bridge.
 *
 * Note: the binary file format has some deficits
 * - Variable length elements (strings, tables in tables, dynamic images) are NOT supported, each chunk must be of fixed size.
 * - Although the JSON or description would allow it, we can only handle tabular data in the moment.
 * - Only BigEndian is supported in the moment. (Ticket #38)
 *
 * @param `type` Mantik model of the data.
 * @param files description of the different files.
 */
@deprecated("Binary decoding should be done by binary bridge.")
case class BinaryDataSetDescription(
    `type`: DataType,
    files: Seq[BinaryFileDescription] = Seq.empty
)

object BinaryDataSetDescription {
  implicit val encoder: ObjectEncoder[BinaryDataSetDescription] = BinaryDataSetDescriptionJsonAdapter.binaryDataSetEncoder
  implicit val decoder: Decoder[BinaryDataSetDescription] = BinaryDataSetDescriptionJsonAdapter.binaryDataSetDecoder
}

/**
 * Describes one file.
 * @param file name of the file
 * @param compression compression method for the file
 * @param skip much bytes to skip at the beginning, default is nothing
 * @param content what's inside the file.
 */
case class BinaryFileDescription(
    file: String,
    compression: Option[Compression] = None,
    skip: Option[Int] = None,
    content: Seq[BinaryFileContent]
)

sealed trait BinaryFileContent

object BinaryFileContent {
  /** Go to the next row, exactly bytes count after this row. There may only one element like this. */
  case class Stride(stride: Int) extends BinaryFileContent
  /** Parse an element in it's plain serialized order. */
  case class Element(element: String) extends BinaryFileContent
  /** Skip some bytes. */
  case class Skip(skip: Int) extends BinaryFileContent
}

/** A Compression method. */
sealed trait Compression

object Compression {
  case object Gzip extends Compression
}
