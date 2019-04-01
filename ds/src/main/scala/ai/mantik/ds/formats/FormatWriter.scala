package ai.mantik.ds.formats

import ai.mantik.ds.DataType
import ai.mantik.ds.element.RootElement
import akka.stream.scaladsl.Sink

import scala.concurrent.Future

/** Represents the writer of a format. */
trait FormatWriter {
  type ReturnType <: Any

  /** Open a sink for this format. */
  def write(dataType: DataType): Future[Sink[RootElement, ReturnType]]
}
