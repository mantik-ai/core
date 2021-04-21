package ai.mantik.ds.sql.run

import ai.mantik.ds.FundamentalType.StringType
import ai.mantik.ds.element.{Bundle, SingleElementBundle}
import ai.mantik.ds.operations.BinaryOperation
import ai.mantik.ds.sql.Select
import ai.mantik.ds.testutil.TestBase
import ai.mantik.ds.{ArrayT, FundamentalType, Nullable, Struct, TabularData}

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
        selector = Some(
          Program(
            OpCode.Get(0),
            OpCode.Constant(Bundle.fundamental(1)),
            OpCode.Equals(FundamentalType.Int32)
          )
        ),
        projector = None,
        result = simpleInput
      )
    )
  }

  it should "compile a double filter" in {
    compile(simpleInput, "select * where x = 1 and y = 'boom'") shouldBe Right(
      SelectProgram(
        Some(DataSource(0, simpleInput)),
        selector = Some(
          Program(
            OpCode.Get(0),
            OpCode.Constant(Bundle.fundamental(1)),
            OpCode.Equals(FundamentalType.Int32),
            OpCode.ReturnOnFalse,
            OpCode.Pop,
            OpCode.Get(1),
            OpCode.Constant(Bundle.fundamental("boom")),
            OpCode.Equals(FundamentalType.StringType)
          )
        ),
        projector = None,
        result = simpleInput
      )
    )
  }

  it should "compile a not filter" in {
    compile(simpleInput, "select * where not(x = 1 and y = 'boom')") shouldBe Right(
      SelectProgram(
        Some(DataSource(0, simpleInput)),
        selector = Some(
          Program(
            OpCode.Get(0),
            OpCode.Constant(Bundle.fundamental(1)),
            OpCode.Equals(FundamentalType.Int32),
            OpCode.Get(1),
            OpCode.Constant(Bundle.fundamental("boom")),
            OpCode.Equals(FundamentalType.StringType),
            OpCode.And,
            OpCode.Neg
          )
        ),
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
        projector = Some(
          Program(
            OpCode.Get(1)
          )
        ),
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
        projector = Some(
          Program(
            OpCode.Get(0),
            OpCode.Constant(Bundle.fundamental(1)),
            OpCode.BinaryOp(FundamentalType.Int32, BinaryOperation.Add),
            OpCode.Cast(FundamentalType.Int32, FundamentalType.Float64)
          )
        ),
        TabularData(
          "$1" -> FundamentalType.Float64
        )
      )
    )
  }

  it should "compile array get/size on nullables" in {
    val input = TabularData(
      "w" -> Nullable(ArrayT(StringType)),
      "x" -> Nullable(FundamentalType.Int32),
      "y" -> ArrayT(Nullable(FundamentalType.Float32)),
      "z" -> Nullable(ArrayT(Nullable(FundamentalType.Float64)))
    )
    compile(input, "select w[x],size(w)") shouldBe Right(
      SelectProgram(
        Some(DataSource(0, input)),
        selector = None,
        projector = Some(
          Program(
            OpCode.Get(0),
            OpCode.UnpackNullableJump(3),
            OpCode.Get(1),
            OpCode.UnpackNullableJump(1, 1),
            OpCode.ArrayGet,
            OpCode.Get(0),
            OpCode.UnpackNullableJump(2, 0),
            OpCode.ArraySize,
            OpCode.PackNullable
          )
        ),
        TabularData(
          "$1" -> Nullable(FundamentalType.StringType),
          "$2" -> Nullable(FundamentalType.Int32)
        )
      )
    )

    compile(input, "select y[x],size(y)") shouldBe Right(
      SelectProgram(
        Some(DataSource(0, input)),
        selector = None,
        projector = Some(
          Program(
            OpCode.Get(2),
            OpCode.Get(1),
            OpCode.UnpackNullableJump(1, 1),
            OpCode.ArrayGet,
            OpCode.Get(2),
            OpCode.ArraySize
          )
        ),
        TabularData(
          "$1" -> Nullable(FundamentalType.Float32),
          "$2" -> FundamentalType.Int32
        )
      )
    )

    compile(input, "select z[1], z[x],size(z)") shouldBe Right(
      SelectProgram(
        Some(DataSource(0, input)),
        selector = None,
        projector = Some(
          Program(
            OpCode.Get(3),
            OpCode.UnpackNullableJump(2),
            OpCode.Constant(Bundle.fundamental(1)),
            OpCode.ArrayGet,
            OpCode.Get(3),
            OpCode.UnpackNullableJump(3),
            OpCode.Get(1),
            OpCode.UnpackNullableJump(1, 1),
            OpCode.ArrayGet,
            OpCode.Get(3),
            OpCode.UnpackNullableJump(2, 0),
            OpCode.ArraySize,
            OpCode.PackNullable
          )
        ),
        TabularData(
          "$1" -> Nullable(FundamentalType.Float64),
          "$2" -> Nullable(FundamentalType.Float64),
          "$3" -> Nullable(FundamentalType.Int32)
        )
      )
    )
  }

  it should "compile struct access on nullables" in {
    val input = TabularData(
      "a" -> Struct(
        "a1" -> FundamentalType.Int32,
        "a2" -> Nullable(FundamentalType.Int32)
      ),
      "b" -> Nullable(
        Struct(
          "b1" -> FundamentalType.Int32,
          "b2" -> Nullable(FundamentalType.Int32)
        )
      )
    )
    val compilationResult = compile(input, "select (a).a1, (a).a2, (b).b1, (b).b2")
    compilationResult shouldBe Right(
      SelectProgram(
        Some(DataSource(0, input)),
        selector = None,
        projector = Some(
          Program(
            OpCode.Get(0),
            OpCode.StructGet(0),
            OpCode.Get(0),
            OpCode.StructGet(1),
            OpCode.Get(1),
            OpCode.UnpackNullableJump(2),
            OpCode.StructGet(0),
            OpCode.PackNullable,
            OpCode.Get(1),
            OpCode.UnpackNullableJump(1),
            OpCode.StructGet(1)
          )
        ),
        TabularData(
          "a1" -> FundamentalType.Int32,
          "a2" -> Nullable(FundamentalType.Int32),
          "b1" -> Nullable(FundamentalType.Int32),
          "b2" -> Nullable(FundamentalType.Int32)
        )
      )
    )
  }
}
