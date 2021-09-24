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
package ai.mantik.componently.utils

import scala.collection.mutable

/** In Scala 2.13 the MultiMap is deprecated. This class mimics behaviour */
class MutableMultiMap[K, V] {
  private val backend = new mutable.HashMap[K, mutable.Set[V]]()

  /** Add a key/value binding. */
  def add(key: K, value: V): Unit = {
    backend.get(key) match {
      case None      => backend.put(key, mutable.Set(value))
      case Some(set) => set += value
    }
  }

  /** Returns the number of keys */
  def keyCount: Int = backend.size

  /** Get all key/value bindings */
  def get(key: K): mutable.Set[V] = {
    backend.getOrElse(key, mutable.Set.empty)
  }

  /** Get value count for a key */
  def valueCount(key: K): Int = {
    backend.get(key).map(_.size).getOrElse(0)
  }

  /** Remove a key */
  def remove(key: K): Unit = {
    backend.remove(key)
  }

  /** Remove a key value binding */
  def remove(key: K, value: V): Unit = {
    backend.get(key) match {
      case None => // nothing
      case Some(values) =>
        values.remove(value)
        if (values.isEmpty) {
          backend.remove(key)
        }
    }
  }

  /** Clear content */
  def clear(): Unit = {
    backend.clear()
  }
}
