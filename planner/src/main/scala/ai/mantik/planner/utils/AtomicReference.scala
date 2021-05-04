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
