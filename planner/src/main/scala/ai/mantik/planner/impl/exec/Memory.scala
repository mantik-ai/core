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
package ai.mantik.planner.impl.exec

import java.util.concurrent.ConcurrentHashMap

import ai.mantik.planner.MemoryId
import ai.mantik.planner.PlanExecutor.InvalidPlanException
import ai.mantik.planner.utils.AtomicReference

/**
  * The memory of [[ai.mantik.planner.PlanExecutor]] implementation.
  * Each value is writable once.
  */
private[impl] class Memory {
  private val storage = new ConcurrentHashMap[MemoryId, Any]()
  private val last = new AtomicReference[Any](null)

  /** Store some value in memory. */
  @throws[InvalidPlanException]("when the variable is already set")
  def put(memoryId: MemoryId, value: Any): Unit = {
    val previous = storage.put(memoryId, value)
    if (previous != null) {
      throw new InvalidPlanException(s"Variable ${memoryId} is written twice")
    }
  }

  /** Get some value from the memory. */
  @throws[InvalidPlanException]("when the variable is not set")
  def get(memoryId: MemoryId): Any = {
    val value = storage.get(memoryId)
    if (value == null) {
      throw new InvalidPlanException(s"Variable ${memoryId} is never set")
    }
    value
  }

  /** Set the last value of the operation. */
  def setLast(value: Any): Unit = {
    last.set(value)
  }

  /** Returns the last value. */
  @throws[InvalidPlanException]("when there was no last value")
  def getLast(): Any = {
    val v = last.get
    if (v == null) {
      throw new InvalidPlanException("Last value never set")
    }
    v
  }

  /** Returns last value or null. */
  def getLastOrNull(): Any = {
    last.get
  }
}
