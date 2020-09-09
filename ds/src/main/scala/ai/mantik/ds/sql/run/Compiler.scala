package ai.mantik.ds.sql.run

import ai.mantik.ds.element.Bundle
import ai.mantik.ds.operations.{ BinaryFunction, BinaryOperation }
import ai.mantik.ds.sql.{ BinaryExpression, CastExpression, ColumnExpression, Condition, ConstantExpression, Expression, Select, SelectProjection }
import ai.mantik.ds.{ DataType, FundamentalType }
import cats.implicits._

import scala.annotation.tailrec

/** Compiles select statements into programs. */
object Compiler {

  def compile(select: Select): Either[String, SelectProgram] = {
    for {
      selector <- compileSelectors(select.selection)
      projector <- compileProjector(select.projections)
    } yield SelectProgram(selector, projector)
  }

  def compileSelectors(selection: List[Condition]): Either[String, Option[Program]] = {
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
  private def combineConditions(subLists: List[Vector[OpCode]]): Vector[OpCode] = {
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
    add(subLists)
    resultBuilder.result()
  }

  def compileProjector(projections: Option[List[SelectProjection]]): Either[String, Option[Program]] = {
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
      case c: BinaryExpression =>
        for {
          leftOps <- compileExpression(c.left)
          rightOps <- compileExpression(c.right)
          op <- binaryOperation(c.op, c.dataType)
        } yield leftOps ++ rightOps :+ op
    }
  }

  private def binaryOperation(op: BinaryOperation, dt: DataType): Either[String, OpCode] = {
    BinaryFunction.findBinaryFunction(op, dt) match {
      case Left(error) => Left(error)
      case Right(_)    => Right(OpCode.BinaryOp(dt, op))
    }
  }
}
