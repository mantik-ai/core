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
package ai.mantik.planner.repository.impl

import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.elements.errors.{ErrorCodes, RemoteRegistryException}
import ai.mantik.elements.registry.api._
import ai.mantik.elements.{ItemId, MantikHeader, MantikId, NamedMantikId}
import ai.mantik.planner.repository.MantikRegistry.PayloadSource
import ai.mantik.planner.repository.{CustomLoginToken, MantikArtifact, MantikRegistry, RemoteMantikRegistry}
import akka.http.scaladsl.Http
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.scalalogging.Logger

import javax.inject.Inject
import net.reactivecore.fhttp.akka.ApiClient

import scala.concurrent.Future
import scala.util.{Success, Try}

private[mantik] class MantikRegistryImpl @Inject() (implicit akkaRuntime: AkkaRuntime)
    extends ComponentBase
    with RemoteMantikRegistry {

  private val registryCredentials = new DefaultRegistryCredentials(config)

  private val executor: ApiClient.RequestExecutor = { request =>
    val t0 = System.currentTimeMillis()
    Http().singleRequest(request).andThen { case Success(response) =>
      val t1 = System.currentTimeMillis()
      logger.debug(s"Calling ${request.method.name()} ${request.uri} ${response.status.intValue()} within ${t1 - t0}ms")
    }
  }
  private val defaultApi = new MantikRegistryApi(registryCredentials.url, executor)
  private val defaultTokenProvider =
    new MantikRegistryTokenProvider(defaultApi, registryCredentials.user, registryCredentials.password)

  /** Provides a token. */
  private[mantik] def token(): Future[String] = defaultTokenProvider.getToken()

  override def get(mantikId: MantikId): Future[MantikArtifact] = {
    defaultCall(getImpl(_, _, mantikId))
  }

  private def getImpl(api: MantikRegistryApi, token: String, mantikId: MantikId): Future[MantikArtifact] = {
    for {
      artifactResponse <- api.artifact(token, mantikId).recoverWith {
        case r: RemoteRegistryException if r.remoteCode == ApiErrorResponse.NotFound =>
          Future.failed(ErrorCodes.MantikItemNotFound.toException(r.message, r))
      }
      artifact <- Future.fromTry(decodeMantikArtifact(mantikId, artifactResponse))
    } yield artifact
  }

  override def ensureMantikId(itemId: ItemId, mantikId: NamedMantikId): Future[Boolean] = {
    defaultCall(ensureMantikIdImpl(_, _, itemId, mantikId))
  }

  private def ensureMantikIdImpl(
      api: MantikRegistryApi,
      token: String,
      itemId: ItemId,
      mantikId: NamedMantikId
  ): Future[Boolean] = {
    api.tag(token, ApiTagRequest(itemId, mantikId)).map(_.updated)
  }

  private def decodeMantikArtifact(
      mantikId: MantikId,
      apiGetArtifactResponse: ApiGetArtifactResponse
  ): Try[MantikArtifact] = {
    for {
      _ <- MantikHeader.fromYaml(apiGetArtifactResponse.mantikDefinition).toTry
    } yield {
      MantikArtifact(
        apiGetArtifactResponse.mantikDefinition,
        fileId = apiGetArtifactResponse.fileId,
        namedId = apiGetArtifactResponse.namedId,
        itemId = apiGetArtifactResponse.itemId
      )
    }
  }

  override def getPayload(fileId: String): Future[PayloadSource] = {
    defaultCall(getPayloadImpl(_, _, fileId))
  }

  private def getPayloadImpl(api: MantikRegistryApi, token: String, fileId: String): Future[PayloadSource] = {
    api.file(token, fileId)
  }

  override def addMantikArtifact(
      mantikArtifact: MantikArtifact,
      payload: Option[PayloadSource]
  ): Future[MantikArtifact] = {
    defaultCall(addMantikArtifactImpl(_, _, mantikArtifact, payload))
  }

  private def addMantikArtifactImpl(
      api: MantikRegistryApi,
      token: String,
      mantikArtifact: MantikArtifact,
      payload: Option[PayloadSource]
  ): Future[MantikArtifact] = {
    for {
      uploadResponse <- api.prepareUpload(
        token,
        ApiPrepareUploadRequest(
          namedId = mantikArtifact.namedId,
          itemId = mantikArtifact.itemId,
          mantikHeader = mantikArtifact.mantikHeader,
          hasFile = payload.nonEmpty
        )
      )
      remoteFileId <- payload match {
        case Some((contentType, source)) =>
          // Akka HTTP Crashes on empty Chunks.
          val withoutEmptyChunks = source.filter(_.nonEmpty)
          api
            .uploadFile(
              token,
              mantikArtifact.itemId.toString,
              contentType,
              withoutEmptyChunks
            )
            .map(response => Some(response.fileId))
        case None =>
          Future.successful(None)
      }
    } yield {
      val result = mantikArtifact.copy(
        fileId = remoteFileId
      )
      result
    }
  }

  override def login(url: String, user: String, password: String): Future[ApiLoginResponse] = {
    val subApi = new MantikRegistryApi(url, executor)
    subApi.login(
      ApiLoginRequest(user, password, MantikRegistryTokenProvider.Requester)
    )
  }

  override def withCustomToken(token: CustomLoginToken): MantikRegistry = return new MantikRegistry {
    override def get(mantikId: MantikId): Future[MantikArtifact] = {
      customCall(token, getImpl(_, _, mantikId))
    }

    override def getPayload(fileId: String): Future[(String, Source[ByteString, _])] = {
      customCall(token, getPayloadImpl(_, _, fileId))
    }

    override def addMantikArtifact(
        mantikArtifact: MantikArtifact,
        payload: Option[(String, Source[ByteString, _])]
    ): Future[MantikArtifact] = {
      customCall(token, addMantikArtifactImpl(_, _, mantikArtifact, payload))
    }

    override def ensureMantikId(itemId: ItemId, mantikId: NamedMantikId): Future[Boolean] = {
      customCall(token, ensureMantikIdImpl(_, _, itemId, mantikId))
    }

    override implicit protected def akkaRuntime: AkkaRuntime = MantikRegistryImpl.this.akkaRuntime

    override protected def logger: Logger = MantikRegistryImpl.this.logger
  }

  /**
    * Runs a call on the default API.
    * @param f called with API and Token
    */
  private def defaultCall[T](f: (MantikRegistryApi, String) => Future[T]): Future[T] = {
    for {
      token <- defaultTokenProvider.getToken()
      response <- f(defaultApi, token)
    } yield response
  }

  /**
    * Runs a call on a own-logged-in-API API.
    * @param f called with API and Token
    */
  private def customCall[T](
      customLoginToken: CustomLoginToken,
      f: (MantikRegistryApi, String) => Future[T]
  ): Future[T] = {
    val subApi = new MantikRegistryApi(customLoginToken.url, executor)
    f(subApi, customLoginToken.token)
  }
}
