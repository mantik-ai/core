package ai.mantik.ds.sql.run

import ai.mantik.ds.Errors.FeatureNotSupported
import ai.mantik.ds.converter.Cast
import ai.mantik.ds.element.{ ArrayElement, Element, NullElement, Primitive, SomeElement, StructElement }
import ai.mantik.ds.operations.BinaryFunction

import scala.collection.mutable

/**
 * A Trivial interpreter for Programs.
 * @throws FeatureNotSupported if some op code could not be translated.
 */
class ProgramRunner(program: Program) {

  type StackType = mutable.ArrayStack[Element]

  /**
   * The operation as it is executed.
   * Returns offset on which to continue
   */
  type ExecutableOp = (IndexedSeq[Element], StackType) => Option[Int]

  private val executables: Vector[ExecutableOp] = program.ops.map(op2ExecutableOp)

  def run(args: IndexedSeq[Element]): IndexedSeq[Element] = {
    require(args.length >= program.args, s"Program needs minimal ${program.args} arguments")
    val stack = new StackType()

    var position = 0
    while (position < executables.size) {
      val op = executables(position)
      op(args, stack) match {
        case Some(offset) => position = position + offset
        case None =>
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
          Some(1)
        }
    }
    def makeTransformContinueOp(f: Element => Element): ExecutableOp = {
      makeContinueOp { s =>
        s.push(f(s.pop()))
      }
    }
    op match {
      case OpCode.Constant(value) =>
        makeContinueOp(_.push(value.element))
      case OpCode.Get(i) =>
        (a, s) => {
          s.push(a(i))
          Some(1)
        }
      case castOp: OpCode.Cast =>
        val cast = eitherOrFeatureNotSupported(Cast.findCast(castOp.from, castOp.to))
        makeTransformContinueOp(cast.op)
      case OpCode.Neg =>
        makeTransformContinueOp { e =>
          Primitive(!e.asInstanceOf[Primitive[Boolean]].x)
        }
      case OpCode.IsNull =>
        makeTransformContinueOp { e =>
          Primitive[Boolean](e == NullElement)
        }
      case OpCode.Equals(_) =>
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
          if (!last) {
            None
          } else {
            Some(1)
          }
        }
      case OpCode.Pop =>
        makeContinueOp { s =>
          s.pop()
        }
      case b: OpCode.BinaryOp =>
        val function = eitherOrFeatureNotSupported(BinaryFunction.findBinaryFunction(b.op, b.dataType))
        makeContinueOp { s =>
          val right = s.pop()
          val left = s.pop()
          s.push(function.op(left, right))
        }
      case OpCode.ArrayGet =>
        makeContinueOp { s =>
          val right = s.pop()
          val left = s.pop()
          val array = left.asInstanceOf[ArrayElement]
          val index = right.asInstanceOf[Primitive[Int]].x - 1 // SQL is 1-based
          if (index >= 0 && index < array.elements.length) {
            val e = array.elements(index) match {
              case s: SomeElement => s
              case NullElement    => NullElement
              case other          => SomeElement(other)
            }
            s.push(e)
          } else {
            s.push(NullElement)
          }
        }
      case OpCode.ArraySize =>
        makeContinueOp { s =>
          val array = s.pop()
          val len = array.asInstanceOf[ArrayElement].elements.size
          s.push(Primitive[Int](len))
        }
      case OpCode.StructGet(index) =>
        makeContinueOp { s =>
          val struct = s.pop().asInstanceOf[StructElement]
          s.push(struct.elements(index))
        }
      case OpCode.UnpackNullableJump(offset, drop) =>
        (_, s) => {
          val value = s.pop()
          value match {
            case SomeElement(value) =>
              s.push(value)
              Some(1)
            case NullElement =>
              for (_ <- 0 until drop) {
                s.pop()
              }
              s.push(NullElement)
              Some(1 + offset)
            case other =>
              throw new IllegalArgumentException(s"Expected nullable, got ${other}")
          }
        }
      case OpCode.PackNullable =>
        makeContinueOp { s =>
          val value = s.pop()
          s.push(SomeElement(value))
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
