package ai.mantik.ds.testutil

import akka.actor.ActorSystem
import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.{ ActorMaterializer, Materializer }
import org.scalatest.BeforeAndAfterAll

/** Adds akka support to a testcase. */
trait GlobalAkkaSupport extends BeforeAndAfterAll {
  self: TestBase =>

  private var _actorSystem: ActorSystem = _
  private var _materializer: Materializer = _

  protected implicit def materializer: Materializer = {
    _materializer
  }

  protected def actorSystem: ActorSystem = _actorSystem

  /** Collect the content of a source. */
  protected def collectSource[T](source: Source[T, _]): Seq[T] = {
    val sink = Sink.seq[T]
    await(source.runWith(sink))
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    _actorSystem = ActorSystem()
    _materializer = ActorMaterializer.create(_actorSystem)
  }

  override protected def afterAll(): Unit = {
    await(_actorSystem.terminate())
    super.afterAll()
  }
}
