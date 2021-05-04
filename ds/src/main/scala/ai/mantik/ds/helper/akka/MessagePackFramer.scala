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
package ai.mantik.ds.helper.akka

import ai.mantik.ds.helper.messagepack.MessagePackHelpers
import akka.NotUsed
import akka.stream._
import akka.stream.scaladsl._
import akka.stream.stage._
import akka.util.ByteString

/** Frames Streams of Message Pack data into single parseable elements */
object MessagePackFramer {
  def make(): Flow[ByteString, ByteString, NotUsed] = {
    Flow.fromGraph(new MessagePackFramer)
  }
}

private class MessagePackFramer extends GraphStage[FlowShape[ByteString, ByteString]] {
  val in = Inlet[ByteString]("MessagePackFramer.in")
  val out = Outlet[ByteString]("MessagePackFramer.out")
  val shape = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape)
    with InHandler
    with OutHandler {
    var buffer: ByteString = ByteString.empty

    override def onPush(): Unit = {
      var pending = grab(in)
      buffer ++= pending
      emitChunk()
    }

    override def onPull(): Unit = {
      emitChunk()
    }

    private def emitChunk(): Unit = {
      MessagePackHelpers.consumableBytes(buffer) match {
        case Some(bytes) =>
          val (first, rest) = buffer.splitAt(bytes)
          buffer = rest
          push(out, first)
        case None =>
          if (isClosed(in)) {
            completeStage()
          } else {
            pull(in)
          }
      }
    }

    override def onUpstreamFinish(): Unit = {
      if (isAvailable(out)) {
        emitChunk()
      }
    }

    setHandlers(in, out, this)
  }
}
