package ai.mantik.ds.sql.run

import io.circe.{ Decoder, ObjectEncoder }

/**
 * A Program for running select statements.
 * It works using a Stack-Based mini virtual machine.
 *
 * @param args the number of arguments (can be more, but will be ignored).
 * @param retStackDepth the length on the stack at the end.
 * @param stackInitDepth how deep the stack should be pre-allocated.
 *
 * Note: argument names is part of communication protocol with a bridge.
 */
case class Program(
    args: Int,
    retStackDepth: Int,
    stackInitDepth: Int,
    ops: Vector[OpCode]
)

object Program {

  /** Convert op list to a program, figures out attributes stack depth automatically */
  def fromOps(ops: Vector[OpCode]): Program = {
    val argCount = getArgCount(ops)
    val (retStackDepth, stackInitDepth) = getStackDepth(ops)
    Program(argCount, retStackDepth, stackInitDepth, ops)
  }

  def apply(ops: OpCode*): Program = fromOps(ops.toVector)

  /** Returns the stack depth at the end and the maximum stack depth. */
  private def getStackDepth(ops: Vector[OpCode]): (Int, Int) = {
    // Note: this is not completely accurat, as early exit can lead
    // to inconsistent currentDepth
    // however we assuming well formed programs here, made for select operations.
    var currentDepth = 0
    var maxDepth = 0
    ops.foreach { code =>
      currentDepth = currentDepth + code.producing - code.consuming
      if (currentDepth > maxDepth) {
        maxDepth = currentDepth
      }
    }
    (currentDepth, maxDepth)
  }

  private def getArgCount(ops: Vector[OpCode]): Int = {
    (ops.collect {
      case OpCode.Get(i) => i
    } :+ -1).max + 1
  }

  implicit val encoder: ObjectEncoder[Program] = ProgramJson.programEncoder
  implicit val decoder: Decoder[Program] = ProgramJson.programDecoder
}