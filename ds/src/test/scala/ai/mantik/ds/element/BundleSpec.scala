package ai.mantik.ds.element

import java.io.File

import ai.mantik.ds.{ FundamentalType, TabularData, Tensor, TypeSamples }
import ai.mantik.ds.testutil.{ GlobalAkkaSupport, TempDirSupport, TestBase }
import PrimitiveEncoder._
import ai.mantik.ds.Errors.EncodingException
import ai.mantik.ds.helper.ZipUtils
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.ByteString

import scala.concurrent.Future

class BundleSpec extends TestBase with TempDirSupport with GlobalAkkaSupport {

  val sampleBundle = Bundle(
    TabularData(
      "id" -> FundamentalType.Int32,
      "name" -> FundamentalType.StringType,
      "void" -> FundamentalType.VoidType
    ),
    Vector(
      TabularRow(FundamentalType.Int32.wrap(1), FundamentalType.StringType.wrap("Alice"), Primitive.unit),
      TabularRow(FundamentalType.Int32.wrap(2), FundamentalType.StringType.wrap("Bob"), Primitive.unit)
    )
  )

  it should "be directly writeable and decodeable from zip files" in {
    val sampleFile1 = tempDirectory.resolve("sample1.zip")
    val result = await(sampleBundle.toZipBundle(sampleFile1))
    result._1.model shouldBe sampleBundle.model
    result._2.count shouldBe >(0L)

    sampleFile1.toFile.exists() shouldBe true

    val decodeResult = await(Bundle.fromZipBundle(sampleFile1))
    decodeResult shouldBe sampleBundle
  }

  it should "be encodable into gzip and from gzip" in {
    val backAgain = await(sampleBundle.asGzip().runWith(Bundle.fromGzip()))
    backAgain shouldBe sampleBundle
  }

  it should "be encodable into a stream without header and back" in {
    val data = collectSource(sampleBundle.encode(withHeader = false))
    val dataAsSource: Source[ByteString, _] = Source(data.toVector)
    val sink: Sink[ByteString, Future[Bundle]] = Bundle.fromStreamWithoutHeader(sampleBundle.model)
    val back = await(dataAsSource.runWith(sink))
    back shouldBe sampleBundle
  }

  it should "be encodable into a stream with header and back" in {
    val data = collectSource(sampleBundle.encode(withHeader = true))
    val dataAsSource: Source[ByteString, _] = Source(data.toVector)
    val sink: Sink[ByteString, Future[Bundle]] = Bundle.fromStreamWithHeader()
    val back = await(dataAsSource.runWith(sink))
    back shouldBe sampleBundle
  }

  it should "be constructable" in {
    val bundle = Bundle.build(
      TabularData(
        "x" -> FundamentalType.Int32,
        "b" -> FundamentalType.BoolType,
        "s" -> FundamentalType.StringType,
        "n" -> FundamentalType.VoidType
      )
    )
      .row(1, true, "Hello World", ())
      .row(2, false, "How are you", ())
      .result
    val expected = Bundle(
      TabularData(
        "x" -> FundamentalType.Int32,
        "b" -> FundamentalType.BoolType,
        "s" -> FundamentalType.StringType,
        "n" -> FundamentalType.VoidType
      ),
      Vector(
        TabularRow(
          Primitive(1), Primitive(true), Primitive("Hello World"), Primitive.unit
        ),
        TabularRow(
          Primitive(2), Primitive(false), Primitive("How are you"), Primitive.unit
        )
      )
    )
    bundle shouldBe expected
  }

  it should "work with single element bundles" in {
    for ((dataType, sample) <- TypeSamples.fundamentalSamples) {
      val bundle = Bundle.build(dataType, sample)
      val encoded = collectByteSource(bundle.encode(true))
      val decoded = await(Source.single(encoded).runWith(Bundle.fromStreamWithHeader()))
      decoded shouldBe bundle
    }
  }

  "toString" should "render the bundle" in {
    Bundle.build(FundamentalType.Int32, Primitive(3)).toString shouldBe "3"
    withClue("It should not crash on illegal values") {
      val badBundle = Bundle(
        FundamentalType.Int32,
        Vector(
          TabularRow(
            TensorElement(IndexedSeq(1))
          )
        )
      )
      badBundle.toString shouldBe "<Error Bundle>"
      intercept[IllegalArgumentException] {
        badBundle.render()
      }
    }
  }

  "deserializers" should "not hang on empty data" in {
    // Regression Bug #47
    val source = Source.empty
    intercept[EncodingException] {
      await(source.runWith(Bundle.fromStreamWithHeader))
    }
  }

  it should "decode empty data when there is no header needed" in {
    val bundle = await(Source.empty.runWith(Bundle.fromStreamWithoutHeader(sampleBundle.model)))
    bundle.model shouldBe sampleBundle.model
    bundle.rows shouldBe empty
  }

  it should "not hang on invalid data" in {
    // Regression Bug #47
    import scala.concurrent.duration._
    val dir = new File(getClass.getResource("/sample_directory").toURI).toPath
    val source = ZipUtils.zipDirectory(dir, 10.seconds)
    intercept[EncodingException] {
      await(source.runWith(Bundle.fromStreamWithHeader))
    }
    intercept[EncodingException] {
      await(source.runWith(Bundle.fromStreamWithoutHeader(sampleBundle.model)))
    }
  }
}
