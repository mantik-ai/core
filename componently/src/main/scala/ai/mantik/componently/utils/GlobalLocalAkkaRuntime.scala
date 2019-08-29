package ai.mantik.componently.utils

import java.time.Clock

import ai.mantik.componently.AkkaRuntime
import akka.actor.ActorSystem
import akka.stream.Materializer
import com.typesafe.config.{ Config, ConfigFactory }

import scala.concurrent.ExecutionContext

/**
 * Helper trait for building testcases which provide an AkkaRuntime.
 * Provides a global akka runtime (outside of the testcases), whose shutdown
 * is at the end of the suite and a local one, whose shutdown is at the end of each test.
 *
 * (The trait exits, as we do not want to make componently a dependency of testutils).
 */
trait GlobalLocalAkkaRuntime {
  // Must be provided by the testcase.
  implicit protected def actorSystem: ActorSystem
  implicit protected def materializer: Materializer
  implicit protected def ec: ExecutionContext
  protected def typesafeConfig: Config

  protected def clock: Clock = Clock.systemUTC()

  private lazy val globalAkkaRuntime = AkkaRuntime.fromRunning(typesafeConfig, clock)

  /** Akka runtime running within one test. */
  private var localAkkaRuntime: AkkaRuntime = _

  /** Tells this trait, that we are entering a testcase, and a local AkkaRuntime should be started. */
  final protected def enterTestcase(): Unit = {
    localAkkaRuntime = globalAkkaRuntime.withExtraLifecycle()
  }

  /** Tells this trait, that we are exiting a testcase and the local akka runtime should be quit. */
  final protected def exitTestcase(): Unit = {
    localAkkaRuntime.shutdown()
    localAkkaRuntime = null
  }

  /** Returns the current valid akka runtime. */
  final protected implicit def akkaRuntime: AkkaRuntime = {
    if (localAkkaRuntime == null) {
      globalAkkaRuntime
    } else {
      localAkkaRuntime
    }
  }
}
