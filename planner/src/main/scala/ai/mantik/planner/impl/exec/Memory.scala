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
