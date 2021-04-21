package ai.mantik.ds.converter

import ai.mantik.ds.FundamentalType.Int32
import ai.mantik.ds._
import ai.mantik.ds.element._
import ai.mantik.testutils.TestBase

class StringPreviewGeneratorSpec extends TestBase {

  "render" should "render fundamentals" in {
    for ((typeName, value) <- TypeSamples.fundamentalSamples) {
      val bundle = Bundle(typeName, Vector(SingleElement(value)))
      StringPreviewGenerator().render(bundle) shouldBe value.x.toString
    }
  }

  val simpleBundle = TabularBundle
    .build(
      TabularData(
        "x" -> FundamentalType.Int32,
        "y" -> FundamentalType.StringType
      )
    )
    .row(1, "Hello")
    .row(2, "World")
    .row(3, "How are you?")
    .result

  it should "render non fundamentals" in {
    TypeSamples.nonFundamentals.foreach { case (dt, example) =>
      val bundle = Bundle(dt, Vector(SingleElement(example)))
      val p = StringPreviewGenerator().render(bundle)
      p shouldNot be(empty)
    }
  }

  it should "render a simple table" in {
    StringPreviewGenerator().render(simpleBundle) shouldBe
      """|x|y           |
        @|-|------------|
        @|1|Hello       |
        @|2|World       |
        @|3|How are you?|
        @""".stripMargin('@')
  }

  it should "limit the row count" in {
    StringPreviewGenerator(maxRows = 2).render(simpleBundle) shouldBe
      """|x|y    |
        @|-|-----|
        @|1|Hello|
        @|2|World|
        @ (2 of 3 Rows)
        @""".stripMargin('@')
  }

  it should "limit cell length" in {
    StringPreviewGenerator(maxCellLength = 5).render(simpleBundle) shouldBe
      """|x|y    |
        @|-|-----|
        @|1|Hello|
        @|2|World|
        @|3|Ho...|
        @""".stripMargin('@')
  }

  it should "render tensors" in {
    def test(
        shape: List[Int],
        flatValue: Seq[Int],
        expected: String,
        renderer: StringPreviewGenerator = StringPreviewGenerator()
    ): Unit = {
      val got = renderer.render(
        SingleElementBundle(Tensor(componentType = Int32, shape = shape), TensorElement(flatValue.toIndexedSeq))
      )
      got shouldBe expected
    }

    test(List(1), Seq(1), "[1]")
    test(List(2), Seq(1, 2), "[1,2]")
    test(List(1, 2), Seq(1, 2), "[[1,2]]")
    test(List(2, 1), Seq(1, 2), "[[1],[2]]")
    test(List(2, 2, 3), Seq(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12), "[[[1,2,3],[4,5,6]],[[7,8,9],[10,11,12]]]")
    test(
      List(2, 2, 3),
      Seq(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12),
      "[[[1,2,...",
      StringPreviewGenerator(maxCellLength = 10)
    )
  }

  it should "fail on too-depth-tensors" in {
    val brutalShape = (1 to 100).toList
    val bundle =
      SingleElementBundle(Tensor(componentType = Int32, shape = brutalShape), TensorElement(IndexedSeq.empty))
    // It won't even read the elements to avoid stack overflow
    StringPreviewGenerator(maxCellLength = 25).render(bundle) shouldBe "Complex Tensor [1,2,3,..."
  }

  it should "render images" in {
    StringPreviewGenerator(maxCellLength = 15).render(
      SingleElementBundle(TypeSamples.image._1, TypeSamples.image._2)
    ) shouldBe "Image(2x3, [..."
  }

  it should "render embedded tables" in {
    val sample = TabularBundle
      .build(
        TabularData(
          "x" -> TabularData(
            "z" -> FundamentalType.Int32,
            "y" -> FundamentalType.StringType
          )
        )
      )
      .row(
        EmbeddedTabularElement(
          Vector(
            TabularRow(Primitive(1), Primitive("Hello")),
            TabularRow(Primitive(2), Primitive("World"))
          )
        )
      )
      .row(EmbeddedTabularElement(Vector.empty))
      .result

    StringPreviewGenerator().render(sample) shouldBe
      """|x                    |
        @|---------------------|
        @|[[1,Hello],[2,World]]|
        @|[]                   |
        @""".stripMargin('@')
  }

  "renderSingleLine" should "also render tables as single value" in {
    StringPreviewGenerator().renderSingleLine(simpleBundle) shouldBe """[[1,Hello],[2,World],[3,How are you?]]"""
  }

  it should "work for all types" in {
    for ((typeName, value) <- TypeSamples.fundamentalSamples) {
      val bundle = Bundle(typeName, Vector(SingleElement(value)))
      StringPreviewGenerator().renderSingleLine(bundle) shouldBe value.x.toString
    }
  }
}
