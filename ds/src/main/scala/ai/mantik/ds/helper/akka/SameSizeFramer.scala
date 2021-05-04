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
import akka.NotUsed
import akka.stream.scaladsl._
import akka.stream._
import akka.util.ByteString
import akka.stream.stage._

object SameSizeFramer {

  /**
    * Splits a stream of ByteStrings into a stream of ByteStrings of the same size.
    * Unneeded elements at the end are ignored.
    */
  def make(count: Int): Flow[ByteString, ByteString, NotUsed] = {
    Flow.fromGraph(new SameSizeFramer(count)).named("byteSkipper")
  }
}

private class SameSizeFramer(desiredSize: Int) extends GraphStage[FlowShape[ByteString, ByteString]] {
  require(desiredSize > 0)

  val in = Inlet[ByteString]("SameSizeFramer.in")
  val out = Outlet[ByteString]("SameSizeFramer.out")

  override val shape = FlowShape.of(in, out)

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
      if (buffer.length >= desiredSize) {
        val result = buffer.take(desiredSize)
        buffer = buffer.drop(desiredSize)
        push(out, result)
      } else {
        if (isClosed(in)) {
          completeStage()
        } else {
          pull(in)
        }
      }
    }

    setHandlers(in, out, this)

    override def onUpstreamFinish(): Unit = {
      if (isAvailable(out)) {
        emitChunk()
      }
    }
  }
}
