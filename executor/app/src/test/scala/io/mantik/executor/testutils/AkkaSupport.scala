package io.mantik.executor.testutils

import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, Materializer }
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.ExecutionContext

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
}
