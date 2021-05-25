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
import ai.mantik.bridge.scalafn.bridge.{BridgeBackend, BridgeException}
import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.ds.functional.FunctionType
import ai.mantik.elements.{MantikDefinition, MantikHeader}
import ai.mantik.mnp.protocol.mnp.{InitRequest, SessionState}
import ai.mantik.mnp.server.ServerSession
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString

import scala.concurrent.Future

class ScalaFnBackend(implicit akkaRuntime: AkkaRuntime) extends ComponentBase with BridgeBackend {
  override def name: String = {
    "ScalaFn"
  }

  override def init(
      initRequest: InitRequest,
      mantikHeader: MantikHeader[_ <: MantikDefinition],
      payload: Option[Source[ByteString, _]],
      stateFn: (SessionState, Option[String]) => Unit
  ): Future[ServerSession] = {
    val header = mantikHeader.toJsonValue.as[ScalaFnHeader] match {
      case Left(error) => throw new BridgeException(s"Could not parse MantikHeader, ${error.getMessage()}", error)
      case Right(ok)   => ok
    }

    decodePayload(payload).map { scalaFnPayload =>
      validateAndStartSession(initRequest, header, scalaFnPayload)
    }
  }

  private def decodePayload(maybePayloadSource: Option[Source[ByteString, _]]): Future[DeserializedPayload] = {
    val source = maybePayloadSource.getOrElse {
      throw new BridgeException(s"ScalaFnBridge always expects a payload")
    }
    val consumer = Sink.fold[ByteString, ByteString](ByteString.empty)((c, n) => c ++ n)
    source.runWith(consumer).map { consumed =>
      ScalaFnPayload.deserialize(consumed)
    }
  }

  private def validateAndStartSession(
      initRequest: InitRequest,
      header: ScalaFnHeader,
      dpayload: DeserializedPayload
  ): ServerSession = {
    val inputCount = header.combiner.input.size
    val outputCount = header.combiner.output.size
    header.fnType match {
      case ScalaFnType.RowMapperType =>
        if (inputCount != 1 || outputCount != 1) {
          throw new BridgeException(s"RowMapper expects 1 input, 1 output, got: ${inputCount}/${outputCount}")
        }
        val rm = dpayload.payload match {
          case rm: RowMapper => rm
          case other         => throw new BridgeException(s"Expected row mapper, got ${other.getClass.getSimpleName}")
        }
        val functionType = FunctionType(
          header.combiner.input.head,
          header.combiner.output.head
        )
        val inputContentType = initRequest.inputs(0).contentType
        val outputContentType = initRequest.outputs(0).contentType
        new RowMapperSession(inputContentType, outputContentType, functionType, rm, dpayload)
    }
  }
}
