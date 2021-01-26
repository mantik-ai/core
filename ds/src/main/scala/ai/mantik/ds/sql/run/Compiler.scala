package ai.mantik.ds.sql.run

import ai.mantik.ds.element.Bundle
import ai.mantik.ds.operations.{ BinaryFunction, BinaryOperation }
import ai.mantik.ds.sql.{ Alias, AnonymousInput, ArrayGetExpression, BinaryOperationExpression, CastExpression, ColumnExpression, Condition, ConstantExpression, Expression, Join, Query, Select, SelectProjection, SizeExpression, StructAccessExpression, Union }
import ai.mantik.ds.{ DataType, FundamentalType, Nullable }
import cats.implicits._

import scala.annotation.tailrec

/** Compiles select statements into programs. */
object Compiler {

  def compile(query: Query): Either[String, TableGeneratorProgram] = {
    query match {
      case a: AnonymousInput => Right(DataSource(a.slot, query.resultingTabularType))
      case s: Select         => compile(s)
      case u: Union          => compile(u)
      case a: Alias          => compile(a.query) // alias is not present program
      case j: Join           => JoinCompiler.compile(j)
    }
  }

  def compile(select: Select): Either[String, SelectProgram] = {
    for {
      input <- compile(select.input)
      selector <- compileSelectors(select.selection)
      projector <- compileProjector(select.projections)
    } yield SelectProgram(Some(input), selector, projector, select.resultingTabularType)
  }

  def compile(union: Union): Either[String, UnionProgram] = {
    for {
      compiledInputs <- union.flat.map(compile).sequence
    } yield UnionProgram(compiledInputs, union.all, union.resultingTabularType, true)
  }

  def compileSelectors(selection: Vector[Condition]): Either[String, Option[Program]] = {
    if (selection.isEmpty) {
      return Right(None)
    }
    val maybeSubLists = selection.map { condition =>
      compileCondition(condition)
    }.sequence

    maybeSubLists.map { subLists =>
      val allInOne = combineConditions(subLists)
      Some(Program.fromOps(allInOne))
    }
  }

  /** Add an early return between multiple conditions. */
  private def combineConditions(subLists: Vector[Vector[OpCode]]): Vector[OpCode] = {
    val resultBuilder = Vector.newBuilder[OpCode]
    @tailrec
    def add(pending: List[Vector[OpCode]]): Unit = {
      pending match {
        case last :: Nil =>
          resultBuilder ++= last
        case notLast :: rest =>
          resultBuilder ++= notLast
          resultBuilder ++= Vector(OpCode.ReturnOnFalse, OpCode.Pop)
          add(rest)
        case Nil =>
          // Special case, no condition given, just put an empty true onto the stack.
          resultBuilder ++= Vector(OpCode.Constant(Bundle.fundamental(true)))
      }
    }
    add(subLists.toList)
    resultBuilder.result()
  }

  def compileProjector(projections: Option[Vector[SelectProjection]]): Either[String, Option[Program]] = {
    val usedProjections = projections match {
      case None              => return Right(None)
      case Some(projections) => projections
    }
    // Projections just put the result onto the stack, so they are just executed after each other
    val maybeSubLists =
      usedProjections.map { projection =>
        compileExpression(projection.expression)
      }.sequence

    maybeSubLists.map { projectionOpCodes =>
      val allInOne = projectionOpCodes.toVector.flatten
      Some(Program.fromOps(allInOne))
    }
  }

  def compileCondition(condition: Condition): Either[String, Vector[OpCode]] = {
    condition match {
      case c: Condition.Not =>
        for {
          base <- compileCondition(c.predicate)
        } yield base :+ OpCode.Neg
      case c: Condition.Equals =>
        for {
          a <- compileExpression(c.left)
          b <- compileExpression(c.right)
        } yield a ++ b :+ OpCode.Equals(c.left.dataType)
      case c: Condition.WrappedExpression => compileExpression(c.expression)
      case and: Condition.And =>
        for {
          a <- compileExpression(and.left)
          b <- compileExpression(and.right)
        } yield {
          a ++ b :+ OpCode.And
        }
      case or: Condition.Or =>
        for {
          a <- compileExpression(or.left)
          b <- compileExpression(or.right)
        } yield {
          a ++ b :+ OpCode.Or
        }
      case isNull: Condition.IsNull =>
        for {
          base <- compileExpression(isNull.expression)
        } yield base :+ OpCode.IsNull
    }
  }

  def compileExpression(expression: Expression): Either[String, Vector[OpCode]] = {
    expression match {
      case c: Condition =>
        compileCondition(c)
      case c: ConstantExpression =>
        Right(Vector(OpCode.Constant(c.value)))
      case c: ColumnExpression =>
        Right(Vector(OpCode.Get(c.columnId)))
      case c: CastExpression =>
        val fromType = c.expression.dataType
        val toType = c.dataType
        val castOp = OpCode.Cast(fromType, toType)
        for {
          base <- compileExpression(c.expression)
        } yield base :+ castOp
      case c: BinaryOperationExpression =>
        for {
          leftOps <- compileExpression(c.left)
          rightOps <- compileExpression(c.right)
          op <- binaryOperation(c.op, c.dataType)
        } yield leftOps ++ rightOps :+ op
      case a: ArrayGetExpression =>
        compileOperationWithPotentialNullableArguments(
          Vector(OpCode.ArrayGet),
          false, // Array Get is always nullable
          a.array,
          a.index
        )
      case a: SizeExpression =>
        compileOperationWithPotentialNullableArguments(
          Vector(OpCode.ArraySize),
          true, // ArraySize may be nullable if array is nullable
          a.expression
        )
      case s: StructAccessExpression =>
        s.underlyingStruct.lookupFieldIndex(s.name) match {
          case None => Left(s"Field ${s.name} not found")
          case Some(fieldIndex) =>
            val needNullWrap = s.expression.dataType.isNullable && !s.underlyingField.isNullable
            compileOperationWithPotentialNullableArguments(
              Vector(OpCode.StructGet(fieldIndex)),
              needNullWrap,
              s.expression
            )
        }
    }
  }

  /**
   * Compiles operation(arguments..)
   * All Arguments are null checked (if nullable) and if necessary null the whole expression
   * will return null.
   *
   * @param opNeedsPotentialNullWrap add a Nullable-Cast at the end if any of the arguments are nullable.
   */
  private def compileOperationWithPotentialNullableArguments(
    operation: Vector[OpCode],
    opNeedsPotentialNullWrap: Boolean,
    arguments: Expression*
  ): Either[String, Vector[OpCode]] = {
    for {
      argumentOpCodes <- arguments.map(compileExpression).toVector.sequence
    } yield {
      val argumentsNullable = arguments.map(_.dataType.isNullable)
      val containsNullable = argumentsNullable.contains(true)
      // Note: reversed
      val resultBuilder = Vector.newBuilder[OpCode]
      var offset = 0

      if (containsNullable && opNeedsPotentialNullWrap) {
        resultBuilder += OpCode.PackNullable
        offset += 1
      }

      resultBuilder ++= operation.reverse
      offset += operation.size
      argumentOpCodes.zip(argumentsNullable).zipWithIndex.reverse.foreach {
        case ((opCodes, isNullable), idx) =>
          if (isNullable) {
            resultBuilder += OpCode.UnpackNullableJump(offset, drop = idx)
            offset += 1
          }
          resultBuilder ++= opCodes.reverse
          offset += opCodes.size
      }
      val result = resultBuilder.result().reverse
      result
    }
  }

  private def binaryOperation(op: BinaryOperation, dt: DataType): Either[String, OpCode] = {
    BinaryFunction.findBinaryFunction(op, dt) match {
      case Left(error) => Left(error)
      case Right(_)    => Right(OpCode.BinaryOp(dt, op))
    }
  }
}
