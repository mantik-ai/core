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
package ai.mantik.mnp

import ai.mantik.componently.rpc.StreamConversions
import ai.mantik.mnp.protocol.mnp.MnpServiceGrpc.MnpService
import ai.mantik.mnp.protocol.mnp.{
  AboutSessionRequest,
  AboutSessionResponse,
  PushRequest,
  PushResponse,
  QuitSessionRequest,
  QuitSessionResponse
}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString

import scala.concurrent.Future

/**
  * Wraps an Mnp Session.
  * @param address address (for debugging and logging)
  * @param sessionId MNP session id
  */
class MnpSession(val address: String, val sessionId: String, mnpService: MnpService) {

  def mnpUrl: String = s"mnp://${address}/${sessionId}"

  def quit(): Future[QuitSessionResponse] = {
    mnpService.quitSession(QuitSessionRequest(sessionId))
  }

  def about(): Future[AboutSessionResponse] = {
    mnpService.aboutSession(
      AboutSessionRequest(
        sessionId
      )
    )
  }

  /** Gives access to task related operations. */
  def task(taskId: String): MnpTask = {
    new MnpTask(sessionId, taskId, mnpService)
  }
}
