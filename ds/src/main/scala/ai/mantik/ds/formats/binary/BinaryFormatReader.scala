package ai.mantik.ds.formats.binary

import java.nio.ByteOrder
import java.nio.file.Path

import ai.mantik.ds.Errors.{ FormatDefinitionException, FormatNotSupportedException }
import ai.mantik.ds.TabularData
import ai.mantik.ds.helper.akka.{ ByteSkipper, SameSizeFramer }
import ai.mantik.ds.natural.{ Element, RootElement, TabularRow }
import akka.stream.scaladsl
import akka.stream.scaladsl.{ FileIO, Source }
import akka.util.{ ByteIterator, ByteString }

/**
 * Reader for [[BinaryDataSetDescription]].
 * Note: this variant assumes the data is already unpacked at a given path.
 */
class BinaryFormatReader(description: BinaryDataSetDescription, directory: Path) {

  /** Currently fixed. */
  implicit val byteOrder: ByteOrder = ByteOrder.BIG_ENDIAN

  private val mainTableStructure = description.model match {
    case t: TabularData => Some(t)
    case _              => None // do we support different formats?
  }

  private def forceMainTableStructure(): TabularData = mainTableStructure.getOrElse {
    throw new FormatNotSupportedException("Binary formats are only supported with tabular data")
  }

  def read(): Source[RootElement, _] = {
    forceMainTableStructure() // forcing table structure

    if (description.files.isEmpty) {
      new IllegalArgumentException(s"No files given")
    }

    // How it Works:
    // We open each file as distinct source and then merge them together into one source combining them together.

    val fileSources = description.files.map { file =>
      openFile(file)
    }.toVector

    val combiner = buildCombiner(fileSources.map(_._1))

    val resultSource: Source[RootElement, _] = Source.zipN(
      fileSources.map(_._2)
    ).map(combiner)

    resultSource
  }

  /**
   * Build a method, which combines fields from different sub sources into one tabular element.
   * Example if mappings is (1,3)(0,2)
   * then each row will look like this:
   * (elements(0)(0), element(1)(0), (element(0)(1), element(1)(1)
   */
  private def buildCombiner(mappings: Seq[Vector[Int]]): Seq[Seq[Element]] => RootElement = {
    val elementCount = forceMainTableStructure().columns.size
    elements => {
      val result = new Array[Element](elementCount)
      val it1 = mappings.iterator
      val elementBlockIterator = elements.iterator
      while (it1.hasNext) {
        val it2 = it1.next().iterator
        val eit2 = elementBlockIterator.next().iterator
        while (it2.hasNext) {
          result(it2.next()) = eit2.next()
        }
      }
      TabularRow(result)
    }
  }

  /**
   * Generate a decoder for a single file inside the binary file bundle.
   * @param file the file to analyze
   * @return a mapping of the output column to real result values and a source of elements.
   */
  private def openFile(file: BinaryFileDescription): (Vector[Int], Source[Seq[Element], _]) = {
    val subFilePath = directory.resolve(file.file)
    if (!subFilePath.startsWith(directory)) {
      throw new FormatDefinitionException("Sub paths may not leave the root directory")
    }

    val mapping = file.content.collect {
      case BinaryFileContent.Element(elementName) =>
        lookupIndex(elementName).getOrElse {
          throw new FormatDefinitionException(s"Could not find definition for ${elementName}")
        }
    }.toVector

    val pureInput = FileIO.fromPath(subFilePath)
    val uncompressed: Source[ByteString, _] = file.compression match {
      case None                   => pureInput
      case Some(Compression.Gzip) => pureInput.via(scaladsl.Compression.gunzip())
    }

    val skippedStart: Source[ByteString, _] = file.skip match {
      case None                           => uncompressed
      case Some(0)                        => uncompressed
      case Some(byteSkip) if byteSkip > 0 => uncompressed.via(ByteSkipper.make(byteSkip))
      case Some(other)                    => throw new FormatDefinitionException(s"Negative byte skip ${other}")
    }

    val chunkSize = chunkByteSize(file)

    val withSameSize: Source[ByteString, _] = skippedStart.via(SameSizeFramer.make(chunkSize))

    val decoder = makeDecoder(file)

    val sourceResult: Source[Seq[Element], _] = withSameSize.map(decoder)

    mapping -> sourceResult
  }

  private def makeDecoder(file: BinaryFileDescription): ByteString => Seq[Element] = {
    val operations: Seq[ByteIterator => Option[Element]] = file.content.collect {
      case BinaryFileContent.Element(elementName) =>
        val dataType = mainTableStructure.flatMap(_.columns.get(elementName)).getOrElse {
          throw new FormatDefinitionException(s"Element not found ${elementName}")
        }
        PlainFormat.plainDecoder(dataType).getOrElse {
          throw new FormatDefinitionException(s"No Decoder for ${elementName} of type ${dataType}")
        }.andThen(Some(_))
      case BinaryFileContent.Skip(s) =>
        val op: ByteIterator => Option[Element] = x => {
          x.drop(s)
          None
        }
        op
    }
    val resultLength = file.content.count(_.isInstanceOf[BinaryFileContent.Element])
    compileDecodingPath(resultLength, operations)
  }

  /**
   * Translate a group of decoding operations working on byte iterators into a single fucntion which decodes the whole byte string.
   * @param resultLength number of result elements
   * @param ops decode operations, they do not have to return values (e.g. skip-Operations)
   * @result compiled operation.
   */
  private def compileDecodingPath(resultLength: Int, ops: Seq[ByteIterator => Option[Element]]): ByteString => Seq[Element] = {
    input =>
      {
        val result = new Array[Element](resultLength)
        val inputIterator = input.iterator
        var cnt = 0
        for {
          op <- ops
          subResult <- op(inputIterator)
        } {
          result(cnt) = subResult
          cnt += 1
        }
        result
      }
  }

  /** Calculate the chunk byte size. */
  private def chunkByteSize(file: BinaryFileDescription): Int = {
    // Two alternatives:
    // Either calculate it from type or summarize it from parts
    val stride = file.content.collect {
      case BinaryFileContent.Stride(stride) => stride
    }.distinct match {
      case Nil         => None
      case Seq(stride) => Some(stride)
      case multiples =>
        throw new FormatDefinitionException(s"Multiple strides found ${multiples}")
    }
    stride.getOrElse {
      file.content.collect {
        case BinaryFileContent.Element(elementName) =>
          val dataType = mainTableStructure.flatMap(_.columns.get(elementName)).getOrElse {
            throw new FormatDefinitionException(s"Element not found ${elementName}")
          }
          val dataTypeSize = PlainFormat.plainSize(dataType).getOrElse {
            throw new FormatDefinitionException(s"No variable length sized elements supported")
          }
          dataTypeSize
        case BinaryFileContent.Skip(skip) =>
          skip
      }.sum
    }
  }

  private def lookupIndex(name: String): Option[Int] = {
    mainTableStructure.flatMap { table =>
      table.lookupColumnIndex(name)
    }
  }
}
