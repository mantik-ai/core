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
package ai.mantik.bridge.scalafn.bridge

import ai.mantik.bridge.protocol.bridge.{BridgeAboutResponse, MantikInitConfiguration}
import ai.mantik.bridge.protocol.bridge.MantikInitConfiguration.Payload
import ai.mantik.componently.Component
import ai.mantik.componently.rpc.RpcConversions
import ai.mantik.elements.{MantikDefinition, MantikHeader}
import ai.mantik.mnp.protocol.mnp.{AboutResponse, InitRequest, SessionState}
import ai.mantik.mnp.server.{ServerBackend, ServerSession}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, Uri}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.google.protobuf.any.Any
import com.typesafe.scalalogging.Logger

import scala.concurrent.Future
import scala.util.control.NonFatal

/** Implements parts of [[ServerBackend]] to make it easier to implement Bridges */
trait BridgeBackend extends ServerBackend with Component {
  import ai.mantik.componently.AkkaHelper._

  /** Returns the name of the Bridge */
  def name: String
  protected def logger: Logger

  final override def about(): Future[AboutResponse] = {
    val bridgeAboutResponse = BridgeAboutResponse()
    val packed = Any.pack(bridgeAboutResponse)
    val result = AboutResponse(
      name,
      Some(packed)
    )
    Future.successful(result)
  }

  /** Initialize a session */
  def init(
      initRequest: InitRequest,
      mantikHeader: MantikHeader[_ <: MantikDefinition],
      payload: Option[Source[ByteString, _]],
      stateFn: (SessionState, Option[String]) => Unit
  ): Future[ServerSession]

  final override def init(
      initRequest: InitRequest,
      stateFn: (SessionState, Option[String]) => Unit
  ): Future[ServerSession] = {
    try {
      val configAny = initRequest.configuration.getOrElse {
        throw new BridgeException(s"No configuration found")
      }
      val initConfiguration = configAny.unpack[MantikInitConfiguration]
      val mantikHeader = MantikHeader.fromYaml(initConfiguration.header) match {
        case Right(ok)   => ok
        case Left(error) => throw new BridgeException(s"Invalid MantikHeader", error)
      }
      fetchPayload(initRequest.sessionId, initConfiguration).flatMap { maybePayload =>
        init(initRequest, mantikHeader, maybePayload, stateFn)
      }
    } catch {
      case b: BridgeException => throw b
      case NonFatal(e)        => throw new BridgeException(s"Decoding init call failed", e)
    }
  }

  private def fetchPayload(
      sessionId: String,
      initConfiguration: MantikInitConfiguration
  ): Future[Option[Source[ByteString, _]]] = {
    initConfiguration.payload match {
      case Payload.Empty =>
        None
        logger.debug(s"No payload for session ${sessionId}")
        Future.successful(None)
      case Payload.Content(value) =>
        logger.debug(s"Session ${sessionId} Using inline payload")
        Future.successful(Some(Source.single(RpcConversions.decodeByteString(value))))
      case Payload.Url(url) =>
        logger.info(s"Session ${sessionId} downloading payload...")
        val httpRequest = HttpRequest(uri = Uri(url))
        Http().singleRequest(httpRequest).map { response =>
          if (!response.status.isSuccess()) {
            throw new BridgeException(s"Got status ${response.status.intValue()} on fetching payload")
          }
          Some(response.entity.dataBytes)
        }
    }
  }

  override def quit(): Future[Unit] = Future.successful(())
}
