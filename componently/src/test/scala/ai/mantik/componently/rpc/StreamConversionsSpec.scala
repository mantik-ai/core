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
package ai.mantik.componently.rpc

import ai.mantik.testutils.{AkkaSupport, TestBase}
import akka.stream.scaladsl.{Keep, Sink, Source}
import io.grpc.stub.StreamObserver

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}

class StreamConversionsSpec extends TestBase with AkkaSupport {

  trait StreamObserverEnv {
    val source = StreamConversions.streamObserverSource[Int]()
    val sink = Sink.seq[Int]
    val (observer, valuesFuture) = source.toMat(sink)(Keep.both).run()
  }

  "streamObserverSource" should "generate a source, generating a stream observer" in new StreamObserverEnv {
    observer.onNext(10)
    observer.onNext(11)
    observer.onCompleted()
    val values = await(valuesFuture)
    values shouldBe Seq(10, 11)
  }

  it should "work for an empty case" in new StreamObserverEnv {
    observer.onCompleted()
    val values = await(valuesFuture)
    values shouldBe empty
  }

  it should "handle errors" in new StreamObserverEnv {
    val rt = new RuntimeException("Boom")
    observer.onNext(10)
    observer.onError(rt)
    awaitException[RuntimeException] {
      valuesFuture
    } shouldBe rt
  }

  class FakeMultiOutFunction[A, R](values: Seq[Try[R]]) {
    var arg: Option[A] = None
    def run(argument: A, streamObserver: StreamObserver[R]): Unit = {
      arg = Some(argument)
      values.foreach {
        case Success(value)     => streamObserver.onNext(value)
        case Failure(exception) => streamObserver.onError(exception)
      }
      streamObserver.onCompleted()
    }
  }

  class CollectingObserver[T] extends StreamObserver[T] {
    var closed = false
    var collector = Seq.newBuilder[T]
    var error: Option[Throwable] = None
    private val promise = Promise[Seq[T]]

    override def onNext(value: T): Unit = collector += value

    override def onError(t: Throwable): Unit = {
      error = Some(t)
      promise.tryFailure(t)
    }

    override def onCompleted(): Unit = {
      closed = true
      promise.trySuccess(collector.result())
    }

    def result: Future[Seq[T]] = {
      promise.future
    }
  }

  trait MultiOutEnv {
    val boomException = new RuntimeException("boom")
    val errorHandler: PartialFunction[Throwable, Throwable] = {
      case c: RuntimeException if c.getMessage.contains("boom") => new RuntimeException("bam!")
    }
  }

  "callMultiOut" should "work in happy case" in new MultiOutEnv {
    val fake = new FakeMultiOutFunction[Int, Int](Seq(Success(2), Success(3)))
    val response = StreamConversions.callMultiOut(errorHandler, fake.run, 123)(Sink.seq)
    await(response) shouldBe Seq(2, 3)
  }

  it should "work for empty" in new MultiOutEnv {
    val fake = new FakeMultiOutFunction[Int, Int](Seq.empty)
    val response = StreamConversions.callMultiOut(errorHandler, fake.run, 123)(Sink.seq)
    await(response) shouldBe empty
  }

  it should "fail on immediate error" in new MultiOutEnv {
    val fake = new FakeMultiOutFunction[Int, Int](Seq(Failure(boomException)))
    val response = StreamConversions.callMultiOut(errorHandler, fake.run, 123)(Sink.seq)
    awaitException[RuntimeException](response).getMessage shouldBe "bam!"
  }

  it should "fail on later error" in new MultiOutEnv {
    val fake = new FakeMultiOutFunction[Int, Int](Seq(Success(2), Failure(boomException)))
    val response = StreamConversions.callMultiOut(errorHandler, fake.run, 123)(Sink.seq)
    awaitException[RuntimeException](response).getMessage shouldBe "bam!"
  }

  "respondMultiOut" should "work in happy case" in new MultiOutEnv {
    val co = new CollectingObserver[Int]
    StreamConversions.respondMultiOut(
      errorHandler,
      co,
      Source(Vector(1, 2))
    )
    await(co.result) shouldBe Seq(1, 2)
  }

  it should "wor for empty" in new MultiOutEnv {
    val co = new CollectingObserver[Int]
    StreamConversions.respondMultiOut(
      errorHandler,
      co,
      Source(Vector.empty)
    )
    await(co.result) shouldBe Seq.empty
  }

  it should "work for immediate error" in new MultiOutEnv {
    val co = new CollectingObserver[Int]
    StreamConversions.respondMultiOut(
      errorHandler,
      co,
      Source.failed(boomException)
    )
    awaitException[RuntimeException](co.result).getMessage shouldBe "bam!"
  }

  it should "work for later error" in new MultiOutEnv {
    val co = new CollectingObserver[Int]
    StreamConversions.respondMultiOut(
      errorHandler,
      co,
      Source.fromIterator { () =>
        new Iterator[Int] {
          var step = 0
          override def hasNext: Boolean = true

          override def next(): Int = {
            if (step == 0) {
              step += 1
              100
            } else {
              throw boomException
            }
          }
        }
      }
    )
    awaitException[RuntimeException](co.result).getMessage shouldBe "bam!"
  }
}
