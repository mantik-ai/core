package ai.mantik.testutils

import akka.actor.ActorSystem
import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.{ ActorMaterializer, Materializer }
import akka.util.ByteString
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.ExecutionContext

// Initializes Akka Actors and related
trait AkkaSupport extends BeforeAndAfterAll {
  self: TestBase =>

  private var _actorSystem: ActorSystem = _
  private var _materializer: Materializer = _

  implicit protected def actorSystem: ActorSystem = _actorSystem
  implicit protected def materializer: Materializer = _materializer
  implicit protected def ec: ExecutionContext = _actorSystem.dispatcher

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    _actorSystem = ActorSystem()
    _materializer = ActorMaterializer.create(_actorSystem)
  }

  override protected def afterAll(): Unit = {
    await(_actorSystem.terminate())
    super.afterAll()
  }

  /** Collect the content of a source. */
  protected def collectSource[T](source: Source[T, _]): Seq[T] = {
    val sink = Sink.seq[T]
    await(source.runWith(sink))
  }

  protected def collectByteSource(source: Source[ByteString, _]): ByteString = {
    val collected = collectSource(source)
    collected.reduce(_ ++ _)
  }
}
