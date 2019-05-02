package ai.mantik.planner.select.run

import ai.mantik.ds.Errors.FeatureNotSupported
import ai.mantik.ds.converter.Cast
import ai.mantik.ds.element.{ Element, Primitive }
import ai.mantik.ds.operations.BinaryFunction

import scala.collection.mutable

/**
 * A Trivial interpreter for Programs.
 * @throws FeatureNotSupported if some op code could not be translated.
 */
class Runner(program: Program) {

  type StackType = mutable.ArrayStack[Element]

  /**
   * The operation as it is executed.
   * Returns true if the code should continue.
   */
  type ExecutableOp = (IndexedSeq[Element], StackType) => Boolean

  private val executables = program.ops.map(op2ExecutableOp)

  def run(args: IndexedSeq[Element]): IndexedSeq[Element] = {
    require(args.length >= program.args, s"Program needs minimal ${program.args} arguments")
    val stack = new StackType()

    val it = executables.iterator
    while (it.hasNext) {
      val op = it.next()
      if (!op(args, stack)) {
        // early exit
        return stack.reverse.toVector
      }
    }

    stack.reverse.toVector
  }

  private def op2ExecutableOp(op: OpCode): ExecutableOp = {
    def makeContinueOp(f: StackType => Unit): ExecutableOp = {
      (_, s) =>
        {
          f(s)
          true
        }
    }
    def makeTransformContinueOp(f: Element => Element): ExecutableOp = {
      makeContinueOp { s =>
        s.push(f(s.pop()))
      }
    }
    op match {
      case OpCode.Constant(_, value) =>
        makeContinueOp(_.push(value))
      case OpCode.Get(i) =>
        (a, s) => {
          s.push(a(i))
          true
        }
      case castOp: OpCode.Cast =>
        val cast = eitherOrFeatureNotSupported(Cast.findCast(castOp.from, castOp.to))
        makeTransformContinueOp(cast.op)
      case OpCode.Neg =>
        makeTransformContinueOp { e =>
          Primitive(!e.asInstanceOf[Primitive[Boolean]].x)
        }
      case OpCode.Equals =>
        makeContinueOp { s =>
          val right = s.pop()
          val left = s.pop()
          s.push(Primitive[Boolean](left == right))
        }
      case OpCode.And =>
        makeContinueOp { s =>
          val result = s.pop().asInstanceOf[Primitive[Boolean]].x && s.pop().asInstanceOf[Primitive[Boolean]].x
          s.push(Primitive(result))
        }
      case OpCode.Or =>
        makeContinueOp { s =>
          val result = s.pop().asInstanceOf[Primitive[Boolean]].x || s.pop().asInstanceOf[Primitive[Boolean]].x
          s.push(Primitive(result))
        }
      case OpCode.ReturnOnFalse =>
        (_, s) => {
          val last = s.last.asInstanceOf[Primitive[Boolean]].x
          if (last == false) {
            false
          } else {
            true
          }
        }
      case OpCode.Pop =>
        (_, s) => {
          s.pop()
          true
        }
      case b: OpCode.BinaryOp =>
        val function = eitherOrFeatureNotSupported(BinaryFunction.findBinaryFunction(b.op, b.ft))
        (_, s) => {
          val right = s.pop()
          val left = s.pop()
          s.push(function.op(left, right))
          true
        }
    }
  }

  private def eitherOrFeatureNotSupported[T](in: Either[String, T]): T = {
    in match {
      case Left(error)  => throw new FeatureNotSupported(error)
      case Right(value) => value
    }
  }
}
