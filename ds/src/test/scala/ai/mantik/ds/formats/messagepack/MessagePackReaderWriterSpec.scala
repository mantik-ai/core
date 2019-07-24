package ai.mantik.ds.formats.messagepack

import java.io.File

import ai.mantik.ds.Errors.EncodingException
import ai.mantik.ds.FundamentalType.{ Int32, StringType }
import ai.mantik.ds.{ DataType, TabularData }
import ai.mantik.ds.element.{ Bundle, RootElement, TabularRow }
import ai.mantik.ds.testutil.{ GlobalAkkaSupport, TempDirSupport, TestBase }
import akka.stream.scaladsl.{ Keep, Sink, Source }
import ai.mantik.ds.element.PrimitiveEncoder._
import akka.util.ByteString

import scala.concurrent.Future

class MessagePackReaderWriterSpec extends TestBase with GlobalAkkaSupport with TempDirSupport {

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
  it should "create readable data streams" in {
    val readerWriter = new MessagePackReaderWriter(sampleBundle.model)

    val source = Source(sampleBundle.rows)
    val collected = collectSource(source.via(readerWriter.encoder()).via(readerWriter.decoder()))
    collected shouldBe sampleBundle.rows
  }

  it should "be possible to auto decode the format" in {
    val readerWriter = new MessagePackReaderWriter(sampleBundle.model)
    val source = Source(sampleBundle.rows)

    val (futureDataType: Future[DataType], futureData: Future[Seq[RootElement]]) = source
      .via(readerWriter.encoder())
      .viaMat(MessagePackReaderWriter.autoFormatDecoder())(Keep.right)
      .toMat(Sink.seq)(Keep.both)
      .run()

    await(futureDataType) shouldBe sampleBundle.model
    await(futureData) shouldBe sampleBundle.rows
  }

  it should "report errors on empty streams" in {
    val decoder = MessagePackReaderWriter.autoFormatDecoder()
    intercept[EncodingException] {
      await(Source.empty.via(decoder).runWith(Sink.seq))
    }
  }

  it should "report errors on good header and broken data" in {
    val encoded = collectByteSource(sampleBundle.encode(true))
    val source = Source(
      List(encoded, ByteString(0x4d.toByte))
    )
    val decoder = MessagePackReaderWriter.autoFormatDecoder()
    intercept[EncodingException] {
      await(source.via(decoder).runWith(Sink.seq))
    }
  }

  it should "be possible to skip the encoding of the header" in {
    val withoutHeader = new MessagePackReaderWriter(sampleBundle.model, withHeader = false)
    val withHeader = new MessagePackReaderWriter(sampleBundle.model, withHeader = true)

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
