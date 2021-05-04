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

import ai.mantik.componently.rpc.RpcConversions
import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.elements.MantikId
import ai.mantik.engine.protos.remote_registry.RemoteRegistryServiceGrpc.RemoteRegistryService
import ai.mantik.engine.protos.remote_registry._
import ai.mantik.planner.repository.{CustomLoginToken, MantikArtifactRetriever, RemoteMantikRegistry}
import ai.mantik.planner.repository.impl.DefaultRegistryCredentials
import javax.inject.Inject

import scala.concurrent.Future

class RemoteRegistryServiceImpl @Inject() (
    remoteRegistry: RemoteMantikRegistry,
    retriever: MantikArtifactRetriever
)(implicit akkaRuntime: AkkaRuntime)
    extends ComponentBase
    with RpcServiceBase
    with RemoteRegistryService {

  override def pullArtifact(request: PullArtifactRequest): Future[PullArtifactResponse] = {
    handleErrors {
      val mantikId = MantikId.fromString(request.mantikId)
      retriever.pull(mantikId, request.token.map(decodeCustomToken)).map { case (artifact, hull) =>
        PullArtifactResponse(
          artifact = Some(Converters.encodeMantikArtifact(artifact)),
          hull = hull.map(Converters.encodeMantikArtifact)
        )
      }
    }
  }

  private def decodeCustomToken(loginToken: LoginToken): CustomLoginToken = {
    CustomLoginToken(url = loginToken.url, token = loginToken.token)
  }

  override def pushArtifact(request: PushArtifactRequest): Future[PushArtifactResponse] = {
    handleErrors {
      val mantikId = MantikId.fromString(request.mantikId)
      retriever.push(mantikId, request.token.map(decodeCustomToken)).map { case (artifact, hull) =>
        PushArtifactResponse(
          artifact = Some(Converters.encodeMantikArtifact(artifact)),
          hull = hull.map(Converters.encodeMantikArtifact)
        )
      }
    }
  }

  override def login(request: LoginRequest): Future[LoginResponse] = {
    handleErrors {
      val defaultCredentials = new DefaultRegistryCredentials(akkaRuntime.config)

      val credentials: LoginCredentials = request.credentials.getOrElse {
        LoginCredentials(
          url = defaultCredentials.url,
          username = defaultCredentials.user,
          password = defaultCredentials.password.read()
        )
      }
      val url = RpcConversions.decodeOptionalString(credentials.url).getOrElse(defaultCredentials.url)

      remoteRegistry
        .login(
          url,
          credentials.username,
          credentials.password
        )
        .map { response =>
          LoginResponse(
            token = Some(
              LoginToken(
                url = url,
                token = response.token
              )
            ),
            validUntil = response.validUntil.map(Converters.encodeInstantToScalaProto)
          )
        }
    }
  }
}
