package ai.mantik.ds.sql

import ai.mantik.ds.{ FundamentalType, TabularData }
import ai.mantik.testutils.TestBase

class QuerySpec extends TestBase {

  val dataType = TabularData("x" -> FundamentalType.Int32)

  "Union.flat" should "work" in {
    val input1 = AnonymousInput(dataType, 0)
    val input2 = AnonymousInput(dataType, 1)
    val input3 = AnonymousInput(dataType, 2)
    val input4 = AnonymousInput(dataType, 3)
    Union(
      input1,
      input2,
      true
    ).flat shouldBe Vector(
        input1, input2
      )

    Union(
      Union(
        input1,
        input2,
        true
      ),
      input3,
      true
    ).flat shouldBe Vector(input1, input2, input3)

    Union(
      Union(
        Union(
          input1,
          input2,
          true
        ),
        input3,
        true
      ), input4, true
    ).flat shouldBe Vector(input1, input2, input3, input4)

    Union(
      Union(
        input1,
        input2,
        false
      ),
      input3,
      true
    ).flat shouldBe Vector(Union(input1, input2, false), input3)

    Union(
      Union(
        input1,
        input2,
        true
      ),
      input3,
      false
    ).flat shouldBe Vector(Union(input1, input2, true), input3)
  }
}
