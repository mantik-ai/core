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

import ai.mantik.componently.utils.Tracked.EndOfLiveException

import scala.concurrent.{ExecutionContext, Future}

/** An object which is versioned and can be tracked, e.g. for building UIs */
class Tracked[T](
    private var _value: T,
    private var _version: Long = 1,
    private var endOfLife: Boolean = false
)(implicit trackingContext: TrackingContext) {
  object lock

  def get(): (T, Long) = {
    lock.synchronized {
      if (endOfLife) {
        throw new EndOfLiveException(s"Object is marked end of live")
      }
      (_value, _version)
    }
  }

  def value: T = {
    lock.synchronized {
      _value
    }
  }

  def version: Long = {
    lock.synchronized {
      _version
    }
  }

  def markEndOfLive(): Unit = {
    lock.synchronized {
      endOfLife = true
    }
    trackingContext.onUpdate(this)
  }

  def update(f: T => T): (T, Long) = {
    val result = lock.synchronized {
      val updated = f(_value)
      _version += 1
      _value = updated
      (_value, _version)
    }
    trackingContext.onUpdate(this)
    result
  }

  def monitor(version: Long)(implicit ec: ExecutionContext): Future[(T, Long)] = {
    lock.synchronized {
      if (this._version > version) {
        // Fast path
        return Future.successful(this._value -> this._version)
      }
    }
    trackingContext.add(this).map { _ =>
      get()
    }
  }

  /** Convenience function, monitor a value if maybeVersion is set. */
  def maybeMonitor(maybeVersion: Option[Long])(implicit ec: ExecutionContext): Future[(T, Long)] = {
    maybeVersion match {
      case None          => Future.successful(get())
      case Some(version) => monitor(version)
    }
  }
}

object Tracked {

  /** Base class for Exceptions */
  class TrackedException(msg: String) extends RuntimeException(msg)

  /** Object is End of live */
  class EndOfLiveException(msg: String = null) extends TrackedException(msg)

  /** Akka is going down. */
  class ShutdownException(msg: String = null) extends TrackedException(msg)
}
