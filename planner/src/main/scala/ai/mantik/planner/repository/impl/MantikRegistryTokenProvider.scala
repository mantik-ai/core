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

import java.time.Instant

import ai.mantik.componently.utils.SecretReader
import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.elements.errors.ErrorCodes
import ai.mantik.elements.registry.api._
import ai.mantik.planner.buildinfo.BuildInfo

import scala.concurrent.Future
import scala.util.{Failure, Success}

/** Provides tokens for talking to the mantik registry */
class MantikRegistryTokenProvider(
    registryApi: MantikRegistryApi,
    user: String,
    password: SecretReader
)(
    implicit akkaRuntime: AkkaRuntime
) extends ComponentBase {

  // Used by ensureToken
  private object tokenLock
  // There is a token retrieval in progress, just wait for it
  private var tokenInProgress: Option[Future[String]] = None
  // there is a token.
  private var token: Option[String] = None
  // token validity
  private var tokenValidUntil: Option[Instant] = None

  /** Returns a valid token, requesting a new one if necessary. */
  def getToken(): Future[String] = {
    tokenLock.synchronized {
      // Case 1: Is there a valid token already
      existingValidToken() match {
        case Some(token) =>
          return Future.successful(token)
        case None =>
          // Case 2: Is there a process looking for a token?
          tokenInProgress match {
            case Some(process) => return process
            case None          =>
              // ensure a new process
              val newProcess = updateTokenExecute()
              tokenInProgress = Some(newProcess)
              return newProcess
          }
      }
    }
  }

  private def existingValidToken(): Option[String] = {
    for {
      t <- token
      v <- tokenValidUntil
      if v.isAfter(clock.instant())
    } yield t
  }

  private def updateTokenExecute(): Future[String] = {
    val loginRequest = ApiLoginRequest(user, password.read(), MantikRegistryTokenProvider.Requester)
    val loginResponse = registryApi.login(loginRequest)

    loginResponse.onComplete {
      case Failure(ok) =>
        logger.info("Could not get a new token")
        tokenLock.synchronized {
          tokenInProgress = None
          token = None
          tokenValidUntil = None
        }
      case Success(value) =>
        tokenLock.synchronized {
          tokenInProgress = None
          token = Some(value.token)
          tokenValidUntil = value.validUntil
        }
    }

    loginResponse.map(_.token).recoverWith { case exception =>
      Future.failed(ErrorCodes.RemoteRegistryCouldNotGetToken.toException("Could not get token", exception))
    }
  }
}

object MantikRegistryTokenProvider {

  /** Request identification which is sent to the remote registry. */
  val Requester = s"MantikEngine ${BuildInfo.gitVersion}"
}
