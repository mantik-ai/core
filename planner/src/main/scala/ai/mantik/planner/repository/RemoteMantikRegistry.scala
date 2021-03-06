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
package ai.mantik.planner.repository

import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.elements.errors.ErrorCodes
import ai.mantik.elements.registry.api.ApiLoginResponse
import ai.mantik.elements.{ItemId, MantikId, NamedMantikId}
import ai.mantik.planner.repository.MantikRegistry.PayloadSource
import ai.mantik.planner.repository.impl.MantikRegistryImpl
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.google.inject.ImplementedBy

import scala.concurrent.Future

/** A Custom login token which can be used to access other Registries / credentials than the default. */
case class CustomLoginToken(
    url: String,
    token: String
)

/** A Remote Mantik Registry (Mantik Hub) */
@ImplementedBy(classOf[MantikRegistryImpl])
trait RemoteMantikRegistry extends MantikRegistry {

  /**
    * Login into a custom URL.
    * This login call is stateless and has no effect on the other operations which are
    * using the default credentials.
    */
  def login(url: String, user: String, password: String): Future[ApiLoginResponse]

  /** Returns a copy with a custom url/token and no automatic token management. */
  def withCustomToken(token: CustomLoginToken): MantikRegistry
}

object RemoteMantikRegistry {

  /** Returns an empty no-op registry. */
  def empty(implicit akkaRuntime: AkkaRuntime): RemoteMantikRegistry = new ComponentBase with RemoteMantikRegistry {
    private val NotFound = Future.failed(ErrorCodes.MantikItemNotFound.toException("Empty Registry"))

    private val InvalidLogin = Future.failed(ErrorCodes.RemoteRegistryFailure.toException("Empty Registry"))

    override def get(mantikId: MantikId): Future[MantikArtifact] = Future.failed(
      ErrorCodes.MantikItemNotFound.toException(s"Empty Registry: ${mantikId} not found")
    )

    override def ensureMantikId(itemId: ItemId, mantikId: NamedMantikId): Future[Boolean] = NotFound

    override def getPayload(fileId: String): Future[PayloadSource] = NotFound

    override def addMantikArtifact(
        mantikArtifact: MantikArtifact,
        payload: Option[(String, Source[ByteString, _])]
    ): Future[MantikArtifact] = {
      NotFound
    }

    override def login(url: String, user: String, password: String): Future[ApiLoginResponse] = InvalidLogin

    override def withCustomToken(token: CustomLoginToken): MantikRegistry = this
  }
}
