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
package ai.mantik.bridge.scalafn

import ai.mantik.bridge.scalafn.ScalaFnPayload.DeserializedPayload
import ai.mantik.bridge.scalafn.bridge.{Transformation, TransformingTask}
import ai.mantik.componently.AkkaRuntime
import ai.mantik.ds.element.{RootElement, TabularRow}
import ai.mantik.ds.functional.FunctionType
import ai.mantik.mnp.server.{ServerSession, ServerTask}
import akka.stream.scaladsl.Flow

import scala.concurrent.Future

class RowMapperSession(
    inputContentType: String,
    outputContentType: String,
    functionType: FunctionType,
    rowMapper: RowMapper,
    deserializedPayload: DeserializedPayload
)(implicit akkaRuntime: AkkaRuntime)
    extends ServerSession {

  private val fn: RootElement => RootElement = { element =>
    // We safely assume types anyway, this casting cannot add more harm
    rowMapper.f(element.asInstanceOf[TabularRow])
  }

  private val transformation = Transformation(
    functionType,
    Flow.fromFunction(fn)
  )

  override def shutdown(): Future[Unit] = {
    Future {
      deserializedPayload.close()
    }(akkaRuntime.executionContext)
  }

  override def runTask(taskId: String): Future[ServerTask] = {
    Future.successful(new TransformingTask(inputContentType, outputContentType, transformation))
  }
}
