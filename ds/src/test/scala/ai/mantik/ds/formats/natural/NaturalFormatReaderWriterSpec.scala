package ai.mantik.ds.formats.natural

import java.io.File

import ai.mantik.ds.FundamentalType.{ Int32, StringType }
import ai.mantik.ds.{ DataType, TabularData }
import ai.mantik.ds.element.{ Bundle, RootElement, TabularRow }
import ai.mantik.ds.testutil.{ GlobalAkkaSupport, TempDirSupport, TestBase }
import akka.stream.scaladsl.{ Keep, Sink, Source }
import ai.mantik.ds.element.PrimitiveEncoder._

import scala.concurrent.Future

class NaturalFormatReaderWriterSpec extends TestBase with GlobalAkkaSupport with TempDirSupport {

  val sampleBundle = Bundle(
    TabularData(
      "x" -> Int32,
      "y" -> StringType
    ),
    Vector(
      TabularRow(
        Int32.wrap(3), StringType.wrap("Hello World")
      ),
      TabularRow(
        Int32.wrap(0), StringType.wrap("Foo")
      )
    )
  )
  val sampleDesc = NaturalFormatDescription(
    sampleBundle.model
  )

  it should "create readable file bundles" in {
    val descWithFile = sampleDesc.copy(
      file = Some("file1")
    )
    val readerWriter = new NaturalFormatReaderWriter(descWithFile)
    val sink = readerWriter.writeDirectory(tempDirectory)
    await(Source(sampleBundle.rows).runWith(sink))

    new File(tempDirectory.toFile, "file1").exists() shouldBe true

    val source = readerWriter.readDirectory(tempDirectory)
    val collected = collectSource(source)
    collected shouldBe sampleBundle.rows
  }

  it should "fail when there is no file given in description" in {
    val readerWriter = new NaturalFormatReaderWriter(sampleDesc)
    intercept[IllegalStateException] {
      readerWriter.writeDirectory(tempDirectory)
    }
    intercept[IllegalStateException] {
      readerWriter.readDirectory(tempDirectory)
    }

  }

  it should "create readable data streams" in {
    val readerWriter = new NaturalFormatReaderWriter(sampleDesc)

    val source = Source(sampleBundle.rows)
    val collected = collectSource(source.via(readerWriter.encoder()).via(readerWriter.decoder()))
    collected shouldBe sampleBundle.rows
  }

  it should "be possible to auto decode the format" in {
    val readerWriter = new NaturalFormatReaderWriter(sampleDesc)
    val source = Source(sampleBundle.rows)

    val (futureDataType: Future[DataType], futureData: Future[Seq[RootElement]]) = source
      .via(readerWriter.encoder())
      .viaMat(NaturalFormatReaderWriter.autoFormatDecoder())(Keep.right)
      .toMat(Sink.seq)(Keep.both)
      .run()

    await(futureDataType) shouldBe sampleBundle.model
    await(futureData) shouldBe sampleBundle.rows
  }

  it should "be possible to skip the encoding of the header" in {
    val withoutHeader = new NaturalFormatReaderWriter(sampleDesc, withHeader = false)
    val withHeader = new NaturalFormatReaderWriter(sampleDesc, withHeader = true)

    val source = Source(sampleBundle.rows)
    val withoutHeaderBytes = await(source.via(withoutHeader.encoder()).toMat(Sink.seq)(Keep.right).run()).reduce(_ ++ _)
    val withHeaderBytes = await(source.via(withHeader.encoder()).toMat(Sink.seq)(Keep.right).run()).reduce(_ ++ _)
    withoutHeaderBytes.size shouldBe <(withHeaderBytes.size)

    val futureData: Future[Seq[RootElement]] = source
      .via(withoutHeader.encoder())
      .viaMat(withoutHeader.decoder())(Keep.right)
      .toMat(Sink.seq)(Keep.right)
      .run()

    await(futureData) shouldBe sampleBundle.rows
  }
}
