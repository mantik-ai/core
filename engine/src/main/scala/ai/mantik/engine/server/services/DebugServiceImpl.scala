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
package ai.mantik.engine.server.services

import java.io.File

import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.elements.NamedMantikId
import ai.mantik.engine.protos.debug.{AddLocalMantikDirectoryRequest, AddLocalMantikDirectoryResponse}
import ai.mantik.engine.protos.debug.DebugServiceGrpc.DebugService
import ai.mantik.planner.PlanningContext
import javax.inject.Inject

import scala.concurrent.Future

class DebugServiceImpl @Inject() (context: PlanningContext)(implicit akkaRuntime: AkkaRuntime)
    extends ComponentBase
    with DebugService
    with RpcServiceBase {

  override def addLocalMantikDirectory(
      request: AddLocalMantikDirectoryRequest
  ): Future[AddLocalMantikDirectoryResponse] = handleErrors {
    val mantikId = if (request.name.isEmpty) {
      None
    } else {
      Some(NamedMantikId.fromString(request.name))
    }
    val idToUse = context.pushLocalMantikItem(
      new File(request.directory).toPath,
      id = mantikId
    )
    Future.successful(
      AddLocalMantikDirectoryResponse(
        idToUse.toString
      )
    )
  }
}
