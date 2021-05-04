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
package ai.mantik.componently

import java.time.Clock

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.typesafe.config.Config

import scala.concurrent.ExecutionContext

/**
  * Helper for implementing [[Component]], gives implicit access to Akka Components
  */
trait AkkaHelper {
  self: Component =>

  protected def config: Config = akkaRuntime.config

  protected def clock: Clock = akkaRuntime.clock

  // Gives implicit access to various core Akka Services.
  protected implicit def executionContext: ExecutionContext = akkaRuntime.executionContext
  protected implicit def materializer: Materializer = akkaRuntime.materializer
  protected implicit def actorSystem: ActorSystem = akkaRuntime.actorSystem
}

/**
  * Similar to AkkaHelper trait, but can be imported where you do not want to change trait hierarchy.
  */
object AkkaHelper {
  def config(implicit akkaRuntime: AkkaRuntime): Config = akkaRuntime.config

  implicit def executionContext(implicit akkaRuntime: AkkaRuntime): ExecutionContext = akkaRuntime.executionContext
  implicit def materializer(implicit akkaRuntime: AkkaRuntime): Materializer = akkaRuntime.materializer
  implicit def actorSystem(implicit akkaRuntime: AkkaRuntime): ActorSystem = akkaRuntime.actorSystem
}
