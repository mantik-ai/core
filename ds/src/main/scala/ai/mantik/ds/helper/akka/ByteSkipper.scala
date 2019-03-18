package ai.mantik.ds.helper.akka

import akka.NotUsed
import akka.stream.{ FlowShape, Inlet, Outlet }
import akka.stream.scaladsl.Flow
import akka.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import akka.stream._
import akka.util.ByteString

object ByteSkipper {

  /** Skips count bytes from a stream ob ByteStrings. */
  def make(count: Int): Flow[ByteString, ByteString, NotUsed] = {
    Flow[ByteString].via(new ByteSkipper(count)).named("byteSkipper")
  }
}

private class ByteSkipper(count: Int) extends GraphStage[FlowShape[ByteString, ByteString]] {

  val in = Inlet[ByteString]("ByteSkipper.in")
  val out = Outlet[ByteString]("ByteSkipper.out")

  override val shape = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) with InHandler with OutHandler {
    var pending = count

    override def onPush(): Unit = {
      val input = grab(in)

      if (pending <= 0) {
        push(out, input)
      } else {
        if (input.size > pending) {
          push(out, input.drop(pending))
          pending = 0
        } else {
          pending -= input.size
          pull(in)
        }
      }
    }

    override def onPull(): Unit = {
      pull(in)
    }

    setHandlers(in, out, this)
  }
}

