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
package ai.mantik.planner.impl

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import ai.mantik.componently.rpc.{RpcConversions, StreamConversions}
import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.ds.element.Bundle
import ai.mantik.ds.helper.ZipUtils
import ai.mantik.elements.errors.MantikAsyncException
import ai.mantik.elements.{MantikId, NamedMantikId}
import ai.mantik.planner.protos.planning_context.PlanningContextServiceGrpc.PlanningContextService
import ai.mantik.planner.protos.planning_context.{
  AddLocalMantikItemRequest,
  ExecuteActionRequest,
  ExecuteActionResponse,
  LoadItemRequest,
  StateRequest
}
import ai.mantik.planner.repository.rpc.Conversions
import ai.mantik.planner._
import ai.mantik.planner.repository.ContentTypes
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.ByteString
import io.circe.{Decoder, Json}
import io.circe.syntax._
import io.circe.parser
import io.grpc.stub.StreamObserver

import javax.inject.Inject
import org.apache.commons.io.FileUtils

import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Await, Future}
import scala.util.control.NonFatal

/** Implements a [[PlanningContext]] on top of a gRpc [[PlanningContextService]] */
class RemotePlanningContextImpl @Inject() (planningContext: PlanningContextService)(implicit akkaRuntime: AkkaRuntime)
    extends ComponentBase
    with PlanningContext {

  override def load(id: MantikId): MantikItem = {
    loadImpl(
      LoadItemRequest(
        mantikId = Conversions.encodeMantikId(id),
        pull = false
      )
    )
  }

  override def pull(id: MantikId): MantikItem = {
    loadImpl(
      LoadItemRequest(
        mantikId = Conversions.encodeMantikId(id),
        pull = true
      )
    )
  }

  private def loadImpl(request: LoadItemRequest): MantikItem = {
    call {
      planningContext.load(request).map { response =>
        val decoded = Conversions.decodeJsonItem[MantikItem](response.itemJson, err => s"Error decoding item ${err}")
        decoded
      }
    }
  }

  override def execute[T](action: Action[T]): T = {
    val request = ExecuteActionRequest(
      actionJson = action.asJson.toString()
    )
    val responseData: Future[ByteString] =
      StreamConversions.callMultiOut(Conversions.decodeErrors, planningContext.execute, request) {
        Sink.fold[ByteString, ExecuteActionResponse](ByteString.empty) { case (current, next) =>
          current ++ RpcConversions.decodeByteString(next.responseJson)
        }
      }

    val responseValue = responseData.map { data =>
      decodeExecuteAction(action, data)
    }
    await(responseValue)
  }

  private def decodeExecuteAction[T](action: Action[T], jsonResponse: ByteString): T = {
    def decodeResult[X: Decoder]: X = {
      Conversions.decodeLargeJsonItem[X](jsonResponse, msg => s"Could not decode action result ${msg}")
    }

    // Note: IntelliJ marks this red, however it compiles
    val result: T = action match {
      case f: Action.FetchAction =>
        decodeResult[Bundle]
      case s: Action.SaveAction =>
        decodeResult[Unit]
      case p: Action.PushAction =>
        decodeResult[Unit]
      case d: Action.Deploy =>
        decodeResult[DeploymentState]
    }
    result
  }

  override def pushLocalMantikItem(dir: Path, id: Option[NamedMantikId]): MantikId = {
    call {
      val file = dir.resolve("MantikHeader")
      val mantikHeaderContent = FileUtils.readFileToString(file.toFile, StandardCharsets.UTF_8)

      val payloadFile = dir.resolve("payload")
      val payload: Option[(String, Source[ByteString, _])] = if (Files.exists(payloadFile)) {
        if (Files.isDirectory(payloadFile)) {
          Some(ContentTypes.ZipFileContentType -> ZipUtils.zipDirectory(payloadFile, 60.seconds))
        } else {
          Some(ContentTypes.OctetStreamContentType -> FileIO.fromPath(payloadFile))
        }
      } else {
        None
      }
      val firstRequest = AddLocalMantikItemRequest(
        mantikHeader = mantikHeaderContent,
        id = id.map(Conversions.encodeMantikId).getOrElse(""),
        contentType = RpcConversions.encodeOptionalString(payload.map(_._1))
      )

      val dataStream = payload
        .map { case (_, byteSource) =>
          byteSource.map { bytes =>
            AddLocalMantikItemRequest(
              data = RpcConversions.encodeByteString(bytes)
            )
          }

        }
        .getOrElse(Source.empty)

      val sink = StreamConversions.callMultiInSingleOutWithHeader(planningContext.addLocalMantikItem, firstRequest)
      dataStream.runWith(sink).map { result =>
        Conversions.decodeMantikId(result.id)
      }
    }
  }

  override def state(item: MantikItem): MantikItemState = {
    call {
      val request = StateRequest(
        itemJson = item.asJson.toString
      )
      planningContext.state(request).map { response =>
        Conversions
          .decodeJsonItem[MantikItemState](response.stateJson, err => s"Could not decode MantikItemState: ${err}")
      }
    }
  }

  private def call[T](f: => Future[T]): T = {
    await(
      Conversions.decodeErrorsIn(
        f
      )
    )
  }

  private def await[T](future: Future[T]): T = {
    try {
      Await.result(future, Duration.Inf)
    } catch {
      case NonFatal(e) => throw new MantikAsyncException(e)
    }
  }

}
