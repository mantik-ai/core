package ai.mantik.testutils

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.ByteString
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
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
    _actorSystem = ActorSystem("testcase", configForAkka())
    _materializer = ActorMaterializer.create(_actorSystem)
  }

  private def configForAkka(): Config = {
    // Overriding Akka Config, so that we do not have to
    // setup SLF4J support for each test library.
    val overrides = ConfigFactory.parseString("""
                                                |akka {
                                                |  loggers = ["akka.event.slf4j.Slf4jLogger"]
                                                |  loglevel = "DEBUG"
                                                |  logging-filter = "akka.event.slf4j.Slf4jLoggingFilter"
                                                |}
                                                |""".stripMargin)
    overrides.withFallback(typesafeConfig)
  }

  override protected def afterAll(): Unit = {
    await(Http().shutdownAllConnectionPools())
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
