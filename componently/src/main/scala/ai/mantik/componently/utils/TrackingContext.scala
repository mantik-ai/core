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

import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.componently.utils.Tracked.ShutdownException
import akka.actor.Cancellable

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration.FiniteDuration

/** Maintains open tracking requests for [[Tracked]] */
class TrackingContext(defaultTimeout: FiniteDuration)(implicit akkaRuntime: AkkaRuntime) extends ComponentBase {

  private case class Callback(
      promise: Promise[Unit],
      cancellable: Cancellable,
      tracked: Tracked[_]
  )

  private var nextId: Int = 1
  private val callbacks = new mutable.HashMap[Int, Callback]()

  private val callbacksByTracked = new mutable.HashMap[Tracked[_], mutable.Set[Int]]
    with mutable.MultiMap[Tracked[_], Int]

  object lock

  /** Add a tracked object */
  def add(tracked: Tracked[_]): Future[Unit] = {
    val promise = Promise[Unit]
    // Protecting that the callback is faster than our registration
    lock.synchronized {
      val id = nextId
      nextId += 1
      val cancellable = akkaRuntime.actorSystem.scheduler.scheduleOnce(defaultTimeout)(onTimeout(id))
      callbacks.put(id, Callback(promise, cancellable, tracked))
      callbacksByTracked.addBinding(tracked, id)
    }
    promise.future
  }

  /** Returns the number of registered callbacks */
  def callbackCount: Int = {
    lock.synchronized {
      callbacks.size
    }
  }

  /** Returns the number of registered callbacks for a trackable. */
  def callbackByTrackedCount(t: Tracked[_]): Int = {
    lock.synchronized {
      callbacksByTracked.get(t).map(_.size).getOrElse(0)
    }
  }

  /** To be called if the tracked object is updated */
  def onUpdate(tracked: Tracked[_]): Unit = {
    val cbs = lock.synchronized {
      val ids = callbacksByTracked.getOrElse(tracked, Nil)
      // Note: we are assuming eager execution here, this is not functional
      val cbs = ids.flatMap { id =>
        callbacks.remove(id)
      }
      callbacksByTracked.remove(tracked)
      cbs
    }
    cbs.foreach { cb =>
      cb.cancellable.cancel()
      cb.promise.trySuccess(())
    }
  }

  /** Callback handler when the long polling is timing out. */
  private def onTimeout(id: Int): Unit = {
    val cbs = lock.synchronized {
      val maybeCallback = callbacks.remove(id)
      maybeCallback.foreach { callback =>
        callbacksByTracked.removeBinding(callback.tracked, id)
      }
      maybeCallback
    }
    cbs.foreach(_.promise.trySuccess(()))
  }

  addShutdownHook {
    Future {
      val cbs = lock.synchronized {
        val result = callbacks.values.toIndexedSeq
        callbacks.clear()
        callbacksByTracked.clear()
        result
      }
      val exception = new ShutdownException(s"Akka is going down")
      cbs.foreach(_.promise.tryFailure(exception))
    }
  }
}
