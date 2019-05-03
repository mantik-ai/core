package ai.mantik.planner.select.run

import ai.mantik.ds.FundamentalType
import ai.mantik.ds.element.{ Bundle, ValueEncoder }
import ai.mantik.testutils.TestBase

class ProgramSpec extends TestBase {

  "fromOps" should "work in empty case" in {
    Program.fromOps(Vector.empty) shouldBe Program(
      0, 0, 0, Vector.empty
    )
  }

  it should "work in average case" in {
    val ops = Vector(
      OpCode.Constant(Bundle.fundamental(1)),
      OpCode.Get(0),
      OpCode.Equals,
      OpCode.Neg,
      OpCode.Get(1),
      OpCode.Equals,
      OpCode.ReturnOnFalse,
      OpCode.Pop,
      OpCode.Constant(Bundle.fundamental(1))
    )
    Program.fromOps(
      ops
    ) shouldBe Program(
      args = 2,
      retStackLength = 1,
      stackInitDepth = 2,
      ops = ops
    )
  }
}
