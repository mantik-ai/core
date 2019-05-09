package ai.mantik.planner.select.run

import ai.mantik.ds.element.ValueEncoder
import ai.mantik.testutils.TestBase

class RunnerSpec extends TestBase {

  it should "work for a simple program" in {
    val program = Program(
      args = 2,
      stackInitDepth = 1,
      retStackDepth = 1,
      ops = Vector(
        OpCode.Get(1),
        OpCode.Get(0)
      )
    )
    val runner = new Runner(program)
    runner.run(IndexedSeq(ValueEncoder(1), ValueEncoder(2))) shouldBe IndexedSeq(ValueEncoder(2), ValueEncoder(1))
  }
}
