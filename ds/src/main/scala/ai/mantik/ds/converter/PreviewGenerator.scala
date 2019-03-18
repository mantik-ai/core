package ai.mantik.ds.converter

import ai.mantik.ds.natural.{ Primitive, RootElement }
import ai.mantik.ds.{ DataType, FundamentalType, Image, TabularData }
import akka.stream.scaladsl.Flow
import akka.stream.{ Attributes, FlowShape, Inlet, Outlet }
import akka.stream.stage.{ GraphStageLogic, GraphStageWithMaterializedValue, InHandler, OutHandler }
import org.slf4j.LoggerFactory

import scala.collection.immutable.ListMap
import scala.concurrent.{ Future, Promise }

/**
 * Generator for preview data.
 * Images: Convert to PNG and reduce size.
 * Element Count: Strip number of elements.
 * Counts count of elements (for analyzing purposes).
 */
class PreviewGenerator(dataFormat: DataType, maxElementCount: Int) {

  val MaxImageWidth = 256
  val MaxImageHeight = 256

  /** Returns the resulting data type. */
  lazy val generatedDataType: DataType = {
    converter.targetType
  }

  /** Returns the converter used for preview generation. */
  lazy val converter: RootElementConverter = {
    dataFormat match {
      case tabularData: TabularData =>
        converterForDataType(dataFormat) match {
          case x: RootElementConverter => x
          case _ =>
            throw new IllegalStateException(s"Something is wrong, tables should also be root converters")
        }
      case otherType =>
        throw new IllegalStateException(s"Only tables as root data type supported right now")
    }
  }

  private def converterForDataType(dataType: DataType): DataTypeConverter = {
    dataType match {
      case tabularData: TabularData =>
        val columnConverters = tabularData.columns.values.map(converterForDataType)

        // ListMap.map converts to a Map which is loosening order, so we
        // convert to a Seq first.
        val updatedColumns = ListMap(tabularData.columns.toSeq.zip(columnConverters).map {
          case ((columnName, _), converter) =>
            columnName -> converter.targetType
        }: _*)

        val updatedTableType = tabularData.copy(
          columns = updatedColumns
        )

        TabularConverter(updatedTableType, columnConverters.toVector)
      case image: Image if ImagePngConverter.canHandle(image) =>
        val (newWidth, newHeight) = ImagePreviewHelper.limitSize(image.width, image.height, MaxImageWidth, MaxImageHeight)
        new ImagePngConverter(image, newWidth, newHeight)
      case image: Image =>
        DataTypeConverter.ConstantConverter(
          FundamentalType.StringType, Primitive(s"Image ${image} not supported")
        )
      case FundamentalType.StringType =>

        DataTypeConverter.fundamental(FundamentalType.StringType) { s: String =>
          restrictStringLength(s, 64)
        }
      case fundamentalType: FundamentalType =>
        // won't be changed
        DataTypeConverter.IdentityConverter(fundamentalType)
    }
  }

  /** If a string is longer than maxLegnth characters, it will be abbreviated with "...". */
  private def restrictStringLength(s: String, maxLength: Int): String = {
    if (s.length >= maxLength) {
      s.take(Math.max(0, maxLength - 3)) + "..."
    } else {
      s
    }
  }

  /** Generates a flow for preview rendering. */
  def makeFlow(): Flow[RootElement, RootElement, Future[Long]] = {
    Flow.fromGraph(new PreviewGeneratorImpl(converter, maxElementCount))
  }
}

private class PreviewGeneratorImpl(rootElementConverter: RootElementConverter, previewElements: Int) extends GraphStageWithMaterializedValue[FlowShape[RootElement, RootElement], Future[Long]] {
  private val logger = LoggerFactory.getLogger(getClass)

  val in = Inlet[RootElement]("PreviewGenerator.in")
  val out = Outlet[RootElement]("PreviewGenerator.out")
  val shape = FlowShape(in, out)

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[Long]) = {
    val result = Promise[Long]
    var pendingElements = previewElements
    var counter = 0L

    val stageLogic = new GraphStageLogic(shape) with InHandler with OutHandler {
      override def onPush(): Unit = {
        val row = grab(in)
        if (pendingElements > 0) {
          val converted = tryOperation {
            rootElementConverter.convert(row)
          }
          emit(out, converted)
          pendingElements -= 1
        }
        counter += 1
        pull(in)
      }

      override def onPull(): Unit = {
        if (!hasBeenPulled(in)) {
          pull(in)
        }
      }

      override def postStop(): Unit = {
        result.trySuccess(counter)
      }

      private def tryOperation[T](f: => T): T = {
        try {
          f
        } catch {
          case e: Exception =>
            logger.warn(s"Failed", e)
            failStage(e)
            result.tryFailure(e)
            throw e
        }
      }

      setHandlers(in, out, this)
    }
    stageLogic -> result.future
  }
}