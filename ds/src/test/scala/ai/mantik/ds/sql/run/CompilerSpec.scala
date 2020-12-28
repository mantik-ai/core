package ai.mantik.ds.sql.run

import ai.mantik.ds.element.Bundle
import ai.mantik.ds.operations.BinaryOperation
import ai.mantik.ds.sql.Select
import ai.mantik.ds.testutil.TestBase
import ai.mantik.ds.{ FundamentalType, TabularData }

class CompilerSpec extends TestBase {

  val simpleInput = TabularData(
    "x" -> FundamentalType.Int32,
    "y" -> FundamentalType.StringType
  )

  private def compile(input: TabularData, statement: String): Either[String, SelectProgram] = {
    for {
      select <- Select.parse(input, statement)
      program <- Compiler.compile(select)
    } yield program
  }

  it should "compile a simple select all" in {
    compile(simpleInput, "select *") shouldBe Right(
      SelectProgram(
        Some(DataSource(0, simpleInput)),
        None,
        None,
        simpleInput
      )
    )
  }

  it should "compile a trivial filter" in {
    compile(simpleInput, "select * where x = 1") shouldBe Right(
      SelectProgram(
        Some(DataSource(0, simpleInput)),
        selector = Some(Program(
          OpCode.Get(0),
          OpCode.Constant(Bundle.fundamental(1)),
          OpCode.Equals(FundamentalType.Int32)
        )),
        projector = None,
        result = simpleInput
      )
    )
  }

  it should "compile a double filter" in {
    compile(simpleInput, "select * where x = 1 and y = 'boom'") shouldBe Right(
      SelectProgram(
        Some(DataSource(0, simpleInput)),
        selector = Some(Program(
          OpCode.Get(0),
          OpCode.Constant(Bundle.fundamental(1)),
          OpCode.Equals(FundamentalType.Int32),
          OpCode.ReturnOnFalse,
          OpCode.Pop,
          OpCode.Get(1),
          OpCode.Constant(Bundle.fundamental("boom")),
          OpCode.Equals(FundamentalType.StringType)
        )),
        projector = None,
        result = simpleInput
      )
    )
  }

  it should "compile a not filter" in {
    compile(simpleInput, "select * where not(x = 1 and y = 'boom')") shouldBe Right(
      SelectProgram(
        Some(DataSource(0, simpleInput)),
        selector = Some(Program(
          OpCode.Get(0),
          OpCode.Constant(Bundle.fundamental(1)),
          OpCode.Equals(FundamentalType.Int32),
          OpCode.Get(1),
          OpCode.Constant(Bundle.fundamental("boom")),
          OpCode.Equals(FundamentalType.StringType),
          OpCode.And,
          OpCode.Neg
        )),
        projector = None,
        result = simpleInput
      )
    )
  }

  it should "compile a trivial select" in {
    compile(simpleInput, "select y") shouldBe Right(
      SelectProgram(
        Some(DataSource(0, simpleInput)),
        selector = None,
        projector = Some(Program(
          OpCode.Get(1)
        )),
        result = TabularData(
          "y" -> FundamentalType.StringType
        )
      )
    )
  }

  it should "work with a more complex select" in {
    compile(simpleInput, "select CAST(x + 1 AS float64)") shouldBe Right(
      SelectProgram(
        Some(DataSource(0, simpleInput)),
        selector = None,
        projector = Some(Program(
          OpCode.Get(0),
          OpCode.Constant(Bundle.fundamental(1)),
          OpCode.BinaryOp(FundamentalType.Int32, BinaryOperation.Add),
          OpCode.Cast(FundamentalType.Int32, FundamentalType.Float64)
        )),
        TabularData(
          "$1" -> FundamentalType.Float64
        )
      )
    )
  }
}
