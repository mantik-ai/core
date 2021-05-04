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

import java.time.Clock

import ai.mantik.componently.AkkaRuntime
import akka.actor.ActorSystem
import akka.stream.Materializer
import com.typesafe.config.{Config, ConfigFactory}

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
