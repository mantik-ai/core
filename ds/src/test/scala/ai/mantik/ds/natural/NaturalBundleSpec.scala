package ai.mantik.ds.natural

import ai.mantik.ds.{ FundamentalType, TabularData }
import ai.mantik.ds.testutil.{ GlobalAkkaSupport, TempDirSupport, TestBase }
import PrimitiveEncoder._

class NaturalBundleSpec extends TestBase with TempDirSupport with GlobalAkkaSupport {

  val sampleBundle = NaturalBundle(
    TabularData(
      "id" -> FundamentalType.Int32,
      "name" -> FundamentalType.StringType
    ),
    Vector(
      TabularRow(FundamentalType.Int32.wrap(1), FundamentalType.StringType.wrap("Alice")),
      TabularRow(FundamentalType.Int32.wrap(2), FundamentalType.StringType.wrap("Bob"))
    )
  )

  it should "be directly writeable and decodeable from zip files" in {
    val sampleFile1 = tempDirectory.resolve("sample1.zip")
    val result = await(sampleBundle.toZipBundle(sampleFile1))
    result._1.model shouldBe sampleBundle.model
    result._2.count shouldBe >(0L)

    sampleFile1.toFile.exists() shouldBe true

    val decodeResult = await(NaturalBundle.fromZipBundle(sampleFile1))
    decodeResult shouldBe sampleBundle
  }

  it should "be encodable into gzip and from gzip" in {
    val backAgain = await(sampleBundle.asGzip().runWith(NaturalBundle.fromGzip()))
    backAgain shouldBe sampleBundle
  }

  it should "be constructable" in {
    val bundle = NaturalBundle.build(
      TabularData(
        "x" -> FundamentalType.Int32,
        "b" -> FundamentalType.BoolType,
        "s" -> FundamentalType.StringType
      )
    )
      .row(1, true, "Hello World")
      .row(2, false, "How are you")
      .result
    val expected = NaturalBundle(
      TabularData(
        "x" -> FundamentalType.Int32,
        "b" -> FundamentalType.BoolType,
        "s" -> FundamentalType.StringType
      ),
      Vector(
        TabularRow(
          Primitive(1), Primitive(true), Primitive("Hello World")
        ),
        TabularRow(
          Primitive(2), Primitive(false), Primitive("How are you")
        )
      )
    )
    bundle shouldBe expected
  }
}
