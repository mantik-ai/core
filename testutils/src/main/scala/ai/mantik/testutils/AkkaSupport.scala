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
