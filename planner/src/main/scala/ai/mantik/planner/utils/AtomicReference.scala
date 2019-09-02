package ai.mantik.planner.utils

/** Scalaish variant of AtomicReference. */
class AtomicReference[T](initial: T) {
  private val v = new java.util.concurrent.atomic.AtomicReference[T](initial)

  /** Returns the current value. */
  def get: T = v.get()

  /** Set the current value. */
  def set(x: T): Unit = v.set(x)

  /** Update the value. */
  def update(f: T => T): T = {
    v.updateAndGet(t => f(t))
  }
}
