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

import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.engine.buildinfo.BuildInfo
import ai.mantik.engine.protos.engine.AboutServiceGrpc.AboutService
import ai.mantik.engine.protos.engine.VersionResponse
import com.google.protobuf.empty.Empty
import javax.inject.Inject

import scala.concurrent.Future

class AboutServiceImpl @Inject() ()(implicit akkaRuntime: AkkaRuntime)
    extends ComponentBase
    with AboutService
    with RpcServiceBase {

  override def version(request: Empty): Future[VersionResponse] = handleErrors {
    val version = s"${BuildInfo.version} (git: ${BuildInfo.gitVersion}  build:${BuildInfo.buildNum})"
    val response = VersionResponse(
      version
    )
    Future.successful(response)
  }
}
