/*
 * This file is part of the Mantik Project.
 * Copyright (c) 2020-2021 Mantik UG (Haftungsbeschränkt)
 * Authors: See AUTHORS file
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.
 *
 * Additionally, the following linking exception is granted:
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with other code, such other code is not for that reason
 * alone subject to any of the requirements of the GNU Affero GPL
 * version 3.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license.
 */
package ai.mantik.ds.sql.run

import io.circe.{Decoder, Encoder, ObjectEncoder}

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
) {

  /** Concatenates two programs, recalculating stack-depth */
  def ++(other: Program): Program = Program.fromOps(ops ++ other.ops)
}

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
    (ops.collect { case OpCode.Get(i) =>
      i
    } :+ -1).max + 1
  }

  implicit val encoder: Encoder.AsObject[Program] = ProgramJson.programEncoder
  implicit val decoder: Decoder[Program] = ProgramJson.programDecoder
}
