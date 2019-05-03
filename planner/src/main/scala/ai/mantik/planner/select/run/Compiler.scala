package ai.mantik.planner.select.run

import ai.mantik.ds.element.Bundle
import ai.mantik.ds.operations.BinaryOperation
import ai.mantik.ds.{ DataType, FundamentalType }
import ai.mantik.planner.select._

import scala.annotation.tailrec

/** Compiles select statements into programs. */
object Compiler {

  def compile(select: Select): Either[String, SelectProgram] = {
    for {
      selector <- compileSelectors(select.selection)
      projector <- compileProjector(select.projections)
    } yield SelectProgram(selector, projector)
  }

  def compileSelectors(selection: List[Condition]): Either[String, Program] = {
    val maybeSubLists = Utils.flatEither(selection.map { condition =>
      compileCondition(condition)
    })

    maybeSubLists.map { subLists =>
      val allInOne = combineConditions(subLists)
      Program.fromOps(allInOne)
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

  def compileProjector(projections: List[SelectProjection]): Either[String, Program] = {
    // Projections just put the result onto the stack, so they are just executed after each other
    val maybeSubLists = Utils.flatEither(
      projections.map { projection =>
        compileExpression(projection.expression)
      }
    )
    maybeSubLists.map { projectionOpCodes =>
      val allInOne = projectionOpCodes.toVector.flatten
      Program.fromOps(allInOne)
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
        } yield a ++ b :+ OpCode.Equals
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
          op <- findOpCode(c.op, c.dataType)
        } yield leftOps ++ rightOps :+ op
    }
  }

  private def findOpCode(op: BinaryOp, dt: DataType): Either[String, OpCode] = {
    val ft = dt match {
      case ft: FundamentalType => ft
      case _                   => return Left("Only fundamental types for binary ops supported")
    }
    val binaryOp = op match {
      case BinaryOp.Add => BinaryOperation.Add
      case BinaryOp.Mul => BinaryOperation.Mul
      case BinaryOp.Div => BinaryOperation.Div
      case BinaryOp.Sub => BinaryOperation.Sub
      case _            => return Left(s"Unsupported op ${op}")
    }
    Right(OpCode.BinaryOp(ft, binaryOp))
  }
}
