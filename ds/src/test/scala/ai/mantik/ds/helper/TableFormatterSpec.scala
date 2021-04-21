package ai.mantik.ds.helper

import ai.mantik.ds.testutil.TestBase

class TableFormatterSpec extends TestBase {

  "format" should "not fail on empty" in {
    TableFormatter.format(Nil, Nil) shouldBe ""
  }

  it should "render nice 1 tables" in {
    val rendered = TableFormatter.format(
      Seq("ABC"),
      Seq(Seq("Hello World"))
    )
    val expected =
      """|ABC        |
        @|-----------|
        @|Hello World|
        @""".stripMargin('@')

    rendered shouldBe expected
  }

  it should "render nice longer tables" in {
    val rendered = TableFormatter.format(
      Seq("Long Header", "B"),
      Seq(
        Seq("X1", "X2"),
        Seq("ergerg", "g4gergnrkengn")
      )
    )
    val expected =
      """|Long Header|B            |
        @|-----------|-------------|
        @|X1         |X2           |
        @|ergerg     |g4gergnrkengn|
        @""".stripMargin('@')
    rendered shouldBe expected
  }

  it should "crash on length violations" in {
    intercept[IllegalArgumentException] {
      TableFormatter.format(Seq("A", "B"), Seq(Seq("C", "D"), Seq("E")))
    }
    intercept[IllegalArgumentException] {
      TableFormatter.format(Seq("A", "B"), Seq(Seq("C", "D"), Seq("E", "F", "G")))
    }
  }
}
