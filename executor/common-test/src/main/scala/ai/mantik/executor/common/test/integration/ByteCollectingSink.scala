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
package ai.mantik.executor.common.test.integration

import ai.mantik.componently.AkkaRuntime
import akka.stream.scaladsl.Sink
import akka.util.ByteString

import scala.concurrent.Future

/** Helper for testing data sinks, collects all bytes and returns them
  * (Can only be used once)
  */
class ByteCollectingSink(implicit akkaRuntime: AkkaRuntime) {
  import ai.mantik.componently.AkkaHelper._

  private val (data, underlyingSink) = Sink.seq[ByteString].preMaterialize()

  lazy val collectedBytes: Future[ByteString] = data.map(_.foldLeft(ByteString.empty)(_ ++ _))

  lazy val sink: Sink[ByteString, Future[Long]] = underlyingSink.mapMaterializedValue { _ =>
    collectedBytes.map(_.size)
  }
}
