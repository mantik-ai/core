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

import akka.actor.Cancellable
import akka.stream.{Attributes, Outlet, SourceShape}
import akka.stream.stage.{GraphStageLogic, GraphStageWithMaterializedValue, OutHandler}

import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

private[utils] class TrackingSource[T](tracked: Tracked[T])(implicit ec: ExecutionContext)
    extends GraphStageWithMaterializedValue[SourceShape[T], Cancellable] {
  override val shape = SourceShape(Outlet[T]("TrackingSource.out"))
  val out = shape.out

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Cancellable) = {
    val logic = new GraphStageLogic(shape) with Cancellable with OutHandler {
      setHandler(out, this)

      val cancelled = new AtomicBoolean(false)

      var currentVersion: Long = 0
      var currentValue: Option[T] = None
      var started = false

      val trackingAsync = getAsyncCallback(onTrackingResult)

      override def preStart(): Unit = {
        val (value, version) = tracked.get()
        currentVersion = version
        currentValue = Some(value)
      }

      override def onPull(): Unit = {
        if (!started) {
          push(out, currentValue.get)
          started = true
          return
        }
        if (cancelled.get()) {
          return
        }
        continueMonitoring()
      }

      private def continueMonitoring(): Unit = {
        tracked.monitor(currentVersion).onComplete { result =>
          trackingAsync.invoke(result)
        }
      }

      private def onTrackingResult(result: Try[(T, Long)]): Unit = {
        if (cancelled.get()) {
          return
        }
        result match {
          case Success((value, version)) =>
            if (version > currentVersion) {
              currentVersion = version
              currentValue = Some(value)
              push(out, value)
            } else {
              continueMonitoring()
            }
          case Failure(_: Tracked.EndOfLiveException) =>
            complete(out)
          case Failure(other) =>
            fail(out, other)
        }
      }

      override def cancel(): Boolean = {
        val oldValue = cancelled.getAndSet(true)
        if (!oldValue) {
          complete(out)
        }
        !oldValue
      }

      override def isCancelled: Boolean = {
        cancelled.get()
      }

    }

    (logic, logic)
  }

}
