package ai.mantik.ds.formats.json

import ai.mantik.ds.{ DataType, FundamentalType, Nullable, TabularData, Tensor, TypeSamples }
import ai.mantik.ds.element.{ Bundle, Element, EmbeddedTabularElement, NullElement, Primitive, SingleElementBundle, SomeElement, TabularBundle, TensorElement }
import ai.mantik.ds.testutil.TestBase
import io.circe.Json
import io.circe.syntax._

class JsonFormatSpec extends TestBase {

  val valueSample = Bundle.fundamental(100)

  val tableSample = TabularBundle.build(
    TabularData(
      "x" -> FundamentalType.Int32,
      "s" -> FundamentalType.StringType
    )
  )
    .row(1, "Hello")
    .row(2, "World")
    .result

  it should "have a clean encoding for tables" in {
    val serialized = JsonFormat.serializeBundle(tableSample)
    serialized shouldBe Json.obj(
      "type" -> (tableSample.model: DataType).asJson,
      "value" -> Json.arr(
        Json.arr(Json.fromInt(1), Json.fromString("Hello")),
        Json.arr(Json.fromInt(2), Json.fromString("World"))
      )
    )

    JsonFormat.deserializeBundle(serialized) shouldBe Right(tableSample)
  }

  it should "be possible to only serialize the value" in {
    val serialized = JsonFormat.serializeBundleValue(tableSample)
    serialized shouldBe Json.arr(
      Json.arr(Json.fromInt(1), Json.fromString("Hello")),
      Json.arr(Json.fromInt(2), Json.fromString("World"))
    )
    val back = JsonFormat.deserializeBundleValue(tableSample.model, serialized)
    back shouldBe Right(tableSample)
  }

  it should "have a clean encoding for single values" in {
    val serialized = JsonFormat.serializeBundle(valueSample)
    serialized shouldBe Json.obj(
      "type" -> Json.fromString("int32"),
      "value" -> Json.fromInt(100)
    )
    JsonFormat.deserializeBundle(serialized) shouldBe Right(valueSample)
  }

  it should "be possible to only serialize single values" in {
    val serialized = JsonFormat.serializeBundleValue(valueSample)
    serialized shouldBe Json.fromInt(100)
    val back = JsonFormat.deserializeBundleValue(valueSample.model, serialized)
    back shouldBe Right(valueSample)
  }

  it should "work for all primitives" in {
    for ((t, v) <- TypeSamples.fundamentalSamples) {
      val bundle = SingleElementBundle(t, v)
      val serialized = JsonFormat.serializeBundle(bundle)
      withClue(s"It should work for ${t}") {
        val deserialized = JsonFormat.deserializeBundle(serialized)
        if (t == FundamentalType.Float32 && (v.x == Float.NaN || v.x == Float.NegativeInfinity || v.x == Float.PositiveInfinity)) {
          // io.circe translates them all to null and parses them to NaN
          // also, equals on NaN is always false
          deserialized.right.get.single.get.asInstanceOf[Primitive[Float]].x.isNaN shouldBe true
        } else if (t == FundamentalType.Float64 && (v.x == Float.NaN || v.x == Float.NegativeInfinity || v.x == Float.PositiveInfinity)) {
          // the same for doubles
          deserialized.right.get.single.get.asInstanceOf[Primitive[Double]].x.isNaN shouldBe true
        } else {
          deserialized shouldBe Right(bundle)
        }
      }
    }
  }

  private def testAsSingleAndAsTabular(t: DataType, v: Element): Unit = {
    val single = SingleElementBundle(t, v)
    val serialized = JsonFormat.serializeBundle(single)
    val deserialized = JsonFormat.deserializeBundle(serialized)
    deserialized shouldBe Right(single)

    val tabular = TabularBundle.build(
      TabularData(
        "x" -> t,
        "y" -> FundamentalType.StringType
      )
    ).row(v, "1")
      .row(v, "2")
      .result
    val serializedTabular = JsonFormat.serializeBundle(tabular)
    val deserializedTabular = JsonFormat.deserializeBundle(serializedTabular)
    deserializedTabular shouldBe Right(tabular)
  }

  it should "work for tensors" in {
    val tensorType = Tensor(FundamentalType.Float32, List(2, 3))
    val tensorElement = TensorElement[Float](IndexedSeq(1.5f, 2.5f, 3.5f, 4.5f, 5.5f, 6.0f))
    testAsSingleAndAsTabular(tensorType, tensorElement)
  }

  it should "work for images" in {
    testAsSingleAndAsTabular(TypeSamples.image._1, TypeSamples.image._2)
  }

  it should "work for simple nullables" in {
    val base = Nullable(FundamentalType.Int32)
    val elements = Seq(NullElement, SomeElement(Primitive(100)))
    for {
      e <- elements
    } {
      testAsSingleAndAsTabular(base, e)
    }
  }

  it should "work for embedded tables" in {
    val singleValue = SingleElementBundle(
      tableSample.model,
      EmbeddedTabularElement(tableSample.rows)
    )
    val encoded = JsonFormat.serializeBundle(singleValue)
    val decoded = JsonFormat.deserializeBundle(encoded)
    decoded shouldBe Right(tableSample) // it decodes not to a single bundle, as the encoding is the same.

    val sample2 = TabularBundle.build(
      TabularData(
        "x" -> tableSample.model,
        "s" -> FundamentalType.StringType
      )
    ).row(EmbeddedTabularElement(tableSample.rows), "Hello")
      .row(EmbeddedTabularElement(Vector.empty), "World")
      .result
    val encodedEmbedded = JsonFormat.serializeBundle(sample2)
    JsonFormat.deserializeBundle(encodedEmbedded) shouldBe Right(sample2)
  }
}
