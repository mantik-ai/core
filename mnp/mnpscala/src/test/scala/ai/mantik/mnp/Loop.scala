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
package ai.mantik.mnp

import ai.mantik.componently.AkkaRuntime
import akka.{ Done, NotUsed }
import akka.stream.scaladsl._
import akka.util.ByteString

import scala.collection.JavaConverters._
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.{ ExecutionContext, Future, Promise }

/**
 * Loops input to output
 * Note: this is blocking, should not be used outside of testcases
 */
class Loop(implicit akkaRuntime: AkkaRuntime) {
  import ai.mantik.componently.AkkaHelper._

  // None is acting as a symbol for last element
  private val blockingQueue = new ArrayBlockingQueue[Option[ByteString]](10)
  private val donePromise = Promise[Done]
  private val pushing = new AtomicBoolean()
  private val pulling = new AtomicBoolean()

  def push(source: Source[ByteString, NotUsed]): Future[Done] = {
    if (!pushing.compareAndSet(false, true)) {
      throw new IllegalStateException(s"Already pushing")
    }
    source.runForeach { byteString =>
      blockingQueue.put(Some(byteString))
    }.andThen {
      case result =>
        blockingQueue.put(None)
        donePromise.tryComplete(result)
    }
  }

  akkaRuntime.lifecycle.addShutdownHook {
    Future {
      donePromise.tryFailure(new RuntimeException(s"Shutting down"))
      blockingQueue.clear()
      blockingQueue.put(None)
    }
  }

  def pull(): Source[ByteString, NotUsed] = {
    if (!pulling.compareAndSet(false, true)) {
      throw new IllegalStateException(s"Already pulling")
    }
    // BlockingQueue.iterator doesn't block if there is no element
    // So we use our own iterator
    val iterator = new Iterator[ByteString] {
      // None         .. Not yet fetched
      // Some(None)   .. Exhausted
      // Some(Some(x) .. Next element present
      private var n: Option[Option[ByteString]] = None

      override def hasNext: Boolean = {
        ensureNext()
        n.get.isDefined
      }

      override def next(): ByteString = {
        ensureNext()
        val result = n.get.getOrElse {
          throw new NoSuchElementException(s"No next element")
        }
        n = None
        result
      }

      private def ensureNext(): Unit = {
        if (n.isEmpty) {
          n = Some(blockingQueue.take())
        }
      }
    }
    Source.fromIterator { () =>
      iterator
    }
  }

  def done: Future[Done] = {
    donePromise.future
  }
}
