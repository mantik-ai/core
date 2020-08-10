package ai.mantik.componently.rpc

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicLong

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{ Keep, Sink, Source }
import io.grpc.stub.StreamObserver
import org.reactivestreams.{ Subscriber, Subscription }

import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.control.NonFatal
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
    sinkFromStreamObserverWithSpecialHandling[T, T, Unit](destination, identity, identity, initialState = (), stateUpdate = { (_, _) => () })
      .mapMaterializedValue(_ => NotUsed)
  }

  /**
   * Generates a Sink from a stream observer with special handling for the first element.
   * @param f function used for the first element.
   * @param g function used for the rest of the elements.
   * @param completer function used when the stream is complete
   *
   * @tparam U sink type
   * @tparam T observer type
   * @tparam S state type (from observer type)
   */
  def sinkFromStreamObserverWithSpecialHandling[U, T, S](
    destination: StreamObserver[T],
    f: U => T,
    g: U => T,
    completer: Unit => Option[T] = { _: Unit => None },
    initialState: S,
    stateUpdate: (S, T) => S
  ): Sink[U, Future[S]] = {
    var subscribed = false
    var first = true
    var state = initialState
    val stateResult = Promise[S]
    val subscriber = new Subscriber[U] {
      override def onSubscribe(s: Subscription): Unit = {
        require(!subscribed, "Can only subscribed once")
        subscribed = true
        s.request(Long.MaxValue)
      }

      override def onNext(t: U): Unit = {
        val got = if (first) {
          first = false
          f(t)
        } else {
          g(t)
        }
        destination.onNext(got)
        state = stateUpdate(state, got)
      }

      override def onError(t: Throwable): Unit = {
        destination.onError(t)
        stateResult.tryFailure(t)
      }

      override def onComplete(): Unit = {
        val maybeLast = completer(())
        maybeLast.foreach { last =>
          destination.onNext(last)
          state = stateUpdate(state, last)
        }
        destination.onCompleted()
        stateResult.trySuccess(state)
      }
    }
    Sink.fromSubscriber(subscriber)
      .mapMaterializedValue(_ => stateResult.future)
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
        private val buf = new ArrayBlockingQueue[T](bufSize)
        private val awaiting = new AtomicLong(0)

        object lock
        private var failed = false
        private var doneReceived = false
        private var doneSent = false

        subscriber.onSubscribe(this)

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
      override def onNext(value: T): Unit = {
        println(s"Single Stream Result ${value}")
        promise.success(value)
      }

      override def onError(t: Throwable): Unit = promise.failure(t)

      override def onCompleted(): Unit = {
        // nothing
      }
    }
    streamObserver -> promise.future
  }

  /**
   * Generates a stream observer which collects many elements into a vector.
   * Do not use in production.
   */
  def streamObserverCollector[T](): (StreamObserver[T], Future[Vector[T]]) = {
    val collector = Vector.newBuilder[T]
    val promise = Promise[Vector[T]]
    val streamObserver = new StreamObserver[T] {
      override def onNext(value: T): Unit = {
        collector += value
      }

      override def onError(t: Throwable): Unit = promise.tryFailure(t)

      override def onCompleted(): Unit = {
        promise.trySuccess(collector.result())
      }
    }
    streamObserver -> promise.future
  }

  /**
   * Generate a StreamObserver with a special handling for the first element
   * and a generic handling for all elements (including first).
   * This can be used, when requesting streamy data, where the first element contains some extra information.
   *
   * @param decodeHeader the method which decodes the first element
   * @param decodeAll the method which decodes all elemensts, including the first
   * @param errorDecoder a partial fucntion for error decoding.
   *
   * @tparam T the element type of the StreamObserver
   * @tparam H the decoded header type
   * @tparam A the decoded type for the whole stream.
   *
   * @return A stream observer, and a future to the decoded header and a source for the result of decodeAll.
   */
  def headerStreamSource[T, H, A](
    decodeHeader: T => H,
    decodeAll: T => A,
    errorDecoder: PartialFunction[Throwable, Throwable] = PartialFunction.empty
  )(implicit ec: ExecutionContext, materializer: Materializer): (StreamObserver[T], Future[(H, Source[A, _])]) = {
    val promise = Promise[(H, Source[A, _])]
    val observer = StreamConversions.splitFirst[T] {
      case Success(value) =>
        val header = decodeHeader(value)
        val constructed: Source[A, StreamObserver[T]] = StreamConversions
          .streamObserverSource[T]().prependMat(Source.single(value))(Keep.left)
          .map(decodeAll)
          .mapError(errorDecoder)
        val (observer, source) = constructed.preMaterialize()
        promise.trySuccess(header -> source)
        observer
      case Failure(error) =>
        promise.tryFailure(error)
        StreamConversions.empty
    }
    observer -> promise.future.transform {
      case Success(ok)                               => Success(ok)
      case Failure(e) if errorDecoder.isDefinedAt(e) => Failure(errorDecoder(e))
      case Failure(e)                                => Failure(e)
    }
  }

  /**
   * Helper to implement streamy input functions with special treating for the first (header) element.
   *
   * The function f is called with the first element and the source of ALL elements (including the first)
   *
   * f is allowed to block, but not too long.
   *
   * This is like the inverse of [[headerStreamSource]]
   *
   * @param errorHandler an error handling function.
   * @param f handler function
   * @return a A Stream Observer which handles the input type.
   */
  def streamingRequest[Input, Output](
    errorHandler: PartialFunction[Throwable, Throwable],
    responseObserver: StreamObserver[Output]
  )(
    f: (Input, Source[Input, _]) => Future[Output]
  )(implicit materializer: Materializer, ec: ExecutionContext): StreamObserver[Input] = {

    def encodeError(e: Throwable): Throwable = {
      if (errorHandler.isDefinedAt(e)) {
        errorHandler(e)
      } else {
        e
      }
    }

    StreamConversions.splitFirst[Input] {
      case Failure(e) =>
        responseObserver.onError(encodeError(e))
        StreamConversions.empty
      case Success(header) =>
        val restSourceBase = StreamConversions.streamObserverSource[Input]()
        try {
          val (observer, restSource) = restSourceBase.preMaterialize()
          val fullInput = restSource.prepend(Source.single(header))
          val resultFuture = f(header, fullInput)
          resultFuture.onComplete {
            case Failure(e) =>
              responseObserver.onError(encodeError(e))
            case Success(ok) =>
              responseObserver.onNext(ok)
              responseObserver.onCompleted()
          }
          observer
        } catch {
          case NonFatal(e) =>
            responseObserver.onError(encodeError(e))
            StreamConversions.empty
        }
    }
  }
}
