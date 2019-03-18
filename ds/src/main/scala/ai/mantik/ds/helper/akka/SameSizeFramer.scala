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

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) with InHandler with OutHandler {
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

