package ai.mantik.componently.rpc

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicLong

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import io.grpc.stub.StreamObserver
import org.reactivestreams.{ Subscriber, Subscription }

import scala.concurrent.{ Future, Promise }
import scala.util.{ Failure, Success, Try }

/** Helper for converting gRpc Stream Fundamentals into Akka Counterparts. */
object StreamConversions {

  /** Represents an akka source as Stream Observer. */
  def pumpSourceIntoStreamObserver[T](in: Source[T, _], destination: StreamObserver[T])(implicit materializer: Materializer): Unit = {
    val sink = sinkFromStreamObserver(destination)
    in.runWith(sink)
  }

  /** Generates a Sink which forwards all to the given stream observer. */
  def sinkFromStreamObserver[T](destination: StreamObserver[T]): Sink[T, NotUsed] = {
    var subscribed = false
    val subscriber = new Subscriber[T] {
      override def onSubscribe(s: Subscription): Unit = {
        require(!subscribed, "Can only subscribed once")
        subscribed = true
        s.request(Long.MaxValue)
      }

      override def onNext(t: T): Unit = {
        destination.onNext(t)
      }

      override def onError(t: Throwable): Unit = {
        destination.onError(t)
      }

      override def onComplete(): Unit = {
        destination.onCompleted()
      }
    }
    Sink.fromSubscriber(subscriber)
  }

  /**
   * Build a stream observer, which splits the first element and calls f, which in turn creates an StreamObserver which is used
   * for the rest of the objects
   */
  def splitFirst[T](f: Try[T] => StreamObserver[T]): StreamObserver[T] = {
    new StreamObserver[T] {
      var backend: StreamObserver[T] = _
      var gotFirst = false

      override def onNext(value: T): Unit = {
        if (!gotFirst) {
          backend = f(Success(value))
          gotFirst = true
        } else {
          backend.onNext(value)
        }
      }

      override def onError(t: Throwable): Unit = {
        if (!gotFirst) {
          backend = f(Failure(t))
          gotFirst = true
        } else {
          backend.onError(t)
        }
      }

      override def onCompleted(): Unit = {
        if (!gotFirst) {
          backend = f(Failure(new NoSuchElementException("Missing first element")))
          gotFirst = true
        } else {
          backend.onCompleted()
        }
      }
    }
  }

  /** Generates a source which materializes to a Stream Observer. */
  def streamObserverSource[T](bufSize: Int = 1): Source[T, StreamObserver[T]] = {
    Source.asSubscriber[T].mapMaterializedValue { subscriber =>
      new StreamObserver[T] with Subscription {
        subscriber.onSubscribe(this)

        private val buf = new ArrayBlockingQueue[T](bufSize)
        private val awaiting = new AtomicLong(0)

        object lock
        private var failed = false
        private var doneReceived = false
        private var doneSent = false

        private def pump(): Unit = {
          // TODO: This looks a bit slow, but pump() can be called indirectly
          // from sending and receiving side
          lock.synchronized {
            while (awaiting.get() > 0 && !buf.isEmpty) {
              val element = buf.take()
              subscriber.onNext(element)
              awaiting.decrementAndGet()
            }
            if (buf.isEmpty) {
              if (doneReceived && !doneSent) {
                subscriber.onComplete()
                doneSent = true
              }
            }
          }
        }

        override def request(n: Long): Unit = {
          awaiting.addAndGet(n)
          pump()
        }

        override def cancel(): Unit = {
          buf.clear()
        }

        override def onNext(value: T): Unit = {
          if (failed) {
            return
          }
          buf.put(value)
          pump()
        }

        override def onError(t: Throwable): Unit = {
          lock.synchronized {
            subscriber.onError(t)
            failed = true
          }
        }

        override def onCompleted(): Unit = {
          lock.synchronized {
            doneReceived = true
          }
          pump()
        }
      }
    }
  }

  def empty[T]: StreamObserver[T] = new StreamObserver[T] {
    override def onNext(value: T): Unit = {}

    override def onError(t: Throwable): Unit = {}

    override def onCompleted(): Unit = {}
  }

  /** Generates a StreamObserver, which takes a single element and puts it into a future. */
  def singleStreamObserverFuture[T](): (StreamObserver[T], Future[T]) = {
    val promise = Promise[T]
    val streamObserver = new StreamObserver[T] {
      override def onNext(value: T): Unit = promise.success(value)

      override def onError(t: Throwable): Unit = promise.failure(t)

      override def onCompleted(): Unit = {
        // nothing
      }
    }
    streamObserver -> promise.future
  }
}
