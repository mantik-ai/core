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

import ai.mantik.componently.rpc.{RpcConversions, StreamConversions}
import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.elements.NamedMantikId
import ai.mantik.planner.protos.planning_context.PlanningContextServiceGrpc.PlanningContextService
import ai.mantik.planner.protos.planning_context._
import ai.mantik.planner.repository.MantikArtifactRetriever
import ai.mantik.planner.repository.rpc.Conversions
import ai.mantik.planner.{Action, ActionMeta, MantikItem, PlanningContext}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import io.circe.syntax._
import io.circe.{Encoder, Json, Printer}
import io.grpc.stub.StreamObserver

import javax.inject.Inject
import scala.concurrent.Future
import scala.util.control.NonFatal

/** Implements the gRpc Server for [[PlanningContext]] */
class RemotePlanningContextServerImpl @Inject() (
    context: PlanningContext,
    retriever: MantikArtifactRetriever
)(implicit akkaRuntime: AkkaRuntime)
    extends ComponentBase
    with PlanningContextService {
  override def load(request: LoadItemRequest): Future[LoadItemResponse] = {
    Conversions.encodeErrorsIn {
      Future {
        val mantikItem = if (request.pull) {
          context.load(request.mantikId)
        } else {
          context.load(request.mantikId)
        }
        LoadItemResponse(
          itemJson = mantikItem.asJson.toString()
        )
      }
    }
  }

  override def execute(request: ExecuteActionRequest, responseObserver: StreamObserver[ExecuteActionResponse]): Unit = {
    // Note: we are streaming the response, as gRpc has a maxmimal limit of 4MB per Message
    val maxChunkSize = 65536
    try {
      val responseJson = executeJsonAction(request.actionJson, request.actionMetaJson)
      // Note: This is memory expensive
      val asBuffer = ByteString(Printer.noSpaces.prettyByteBuffer(responseJson))

      val source = Source.fromIterator { () =>
        val groups = asBuffer.grouped(maxChunkSize)
        groups.map { group =>
          ExecuteActionResponse(
            responseJson = RpcConversions.encodeByteString(group)
          )
        }
      }

      StreamConversions.respondMultiOut(Conversions.encodeErrors, responseObserver, source)
    } catch {
      case NonFatal(e) =>
        throw Conversions.encodeErrorIfPossible(e)
    }
  }

  private def executeJsonAction(actionJson: String, actionMetaJson: String): Json = {
    val action = Conversions.decodeJsonItem[Action[_]](actionJson, err => s"Invalid Action ${err}")
    val actionMeta = Conversions.decodeJsonItem[ActionMeta](actionMetaJson, err => s"Invalid Action Meta ${err}")

    def runAndEncode[T: Encoder](action: Action[T]): Json = {
      val result = context.execute(action, actionMeta)
      result.asJson
    }

    // Is there a more beautiful way to encode this discrimination?
    val response = action match {
      case f: Action.FetchAction =>
        runAndEncode(f)
      case s: Action.SaveAction =>
        runAndEncode(s)
      case p: Action.PushAction =>
        runAndEncode(p)
      case d: Action.Deploy =>
        runAndEncode(d)
    }

    response
  }

  override def state(request: StateRequest): Future[StateResponse] = {
    Conversions.encodeErrorsIn {
      Future {
        val item = Conversions.decodeJsonItem[MantikItem](request.itemJson, err => s"Invalid Mantik Item ${err}")
        val state = context.state(item)
        StateResponse(
          stateJson = state.asJson.toString()
        )
      }
    }
  }

  override def addLocalMantikItem(
      responseObserver: StreamObserver[AddLocalMantikItemResponse]
  ): StreamObserver[AddLocalMantikItemRequest] = {
    StreamConversions.respondMultiInSingleOutWithHeader[AddLocalMantikItemRequest, AddLocalMantikItemResponse](
      Conversions.decodeErrors,
      responseObserver
    ) { case (header, source) =>
      Conversions.encodeErrorsIn {
        val mantikId: Option[NamedMantikId] =
          RpcConversions.decodeOptionalString(header.id).map(NamedMantikId.fromString)
        val payloadSource: Option[(String, Source[ByteString, _])] = if (header.contentType.isEmpty) {
          None
        } else {
          Some(header.contentType -> source.map { e => RpcConversions.decodeByteString(e.data) })
        }
        retriever.addMantikItemToRepository(header.mantikHeader, mantikId, payloadSource).map { artifact =>
          AddLocalMantikItemResponse(
            id = Conversions.encodeMantikId(artifact.mantikId)
          )
        }
      }
    }
  }
}
