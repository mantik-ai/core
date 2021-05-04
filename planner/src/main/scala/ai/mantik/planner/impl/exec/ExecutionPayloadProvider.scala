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
package ai.mantik.planner.impl.exec

import ai.mantik.componently.di.ConfigurableDependencies
import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.elements.ItemId
import ai.mantik.executor.ExecutorFileStorage
import ai.mantik.planner.repository.{FileRepository, MantikArtifact, Repository}
import akka.stream.scaladsl.Keep

import java.time.temporal.{ChronoUnit, UnsupportedTemporalTypeException}
import javax.inject.Singleton
import scala.concurrent.{Future, duration}
import scala.concurrent.duration._
import scala.util.control.NonFatal
import cats.implicits._

/** Responsible for providing payload data on the Executor */
private[mantik] trait ExecutionPayloadProvider {

  /** Key under which a temporary file can be found */
  type TemporaryFileKey

  /** Provide temporary access for a file, returns URL */
  def provideTemporary(fileId: String): Future[(TemporaryFileKey, String)]

  /** Undo temporary access. */
  def undoTemporary(keys: Seq[TemporaryFileKey]): Future[Unit]

  /**
    * Provide permanent access for a file, returns URL
    * @return permanent URL or none if there is no payload
    */
  def providePermanent(itemId: ItemId): Future[Option[String]]

  /** Undo permanent access for a file. */
  def undoPermanent(itemId: ItemId): Future[Unit]
}

class ExecutionPayloadProviderModule(implicit akkaRuntime: AkkaRuntime) extends ConfigurableDependencies {
  override protected val configKey: String = "mantik.planner.payloadProvider"

  val executorVariant = "executor"
  val localVariant = "local"

  override protected def variants: Seq[Classes] = Seq(
    variation[ExecutionPayloadProvider](
      executorVariant -> classOf[ExecutorStorageExecutionPayloadProvider],
      localVariant -> classOf[LocalServerExecutionPayloadProvider]
    )
  )
}
