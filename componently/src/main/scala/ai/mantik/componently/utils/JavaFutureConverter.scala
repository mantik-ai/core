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

import java.util.concurrent.CompletionStage
import java.util.function.BiConsumer
import scala.concurrent.{Future, Promise}

object JavaFutureConverter {

  /** Extends CompletionStage with conversions to Scala. */
  implicit class CompletionStageExtensions[T](completionStage: CompletionStage[T]) {
    def asScala: Future[T] = {
      val promise = Promise[T]
      completionStage.whenComplete(new BiConsumer[T, Throwable] {
        override def accept(t: T, u: Throwable): Unit = {
          if (u != null) {
            promise.failure(u)
          } else {
            promise.success(t)
          }
        }
      })
      promise.future
    }
  }
}
