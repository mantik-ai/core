package ai.mantik.engine.server.services

import java.nio.charset.StandardCharsets

import ai.mantik.ds.DataType
import ai.mantik.ds.element.Bundle
import ai.mantik.ds.formats.json.JsonFormat
import ai.mantik.engine.protos.items.MantikItem.Item
import ai.mantik.engine.protos.items.ObjectKind
import ai.mantik.planner.{ Algorithm, DataSet, MantikItem, TrainableAlgorithm }
import ai.mantik.engine.protos.items.{ ObjectKind, MantikItem => ProtoMantikItem }
import ai.mantik.engine.protos.items.{ Algorithm => ProtoAlgorithm }
import ai.mantik.engine.protos.items.{ DataSet => ProtoDataSet }
import ai.mantik.engine.protos.items.{ TrainableAlgorithm => ProtoTrainableAlgorithm }
import ai.mantik.engine.protos.ds.{ BundleEncoding, Bundle => ProtoBundle, DataType => ProtoDataType }
import com.google.protobuf.{ ByteString => ProtoByteString }
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.ByteString
import akka.util.ccompat.IterableOnce
import io.circe.syntax._
import io.circe.parser

import scala.concurrent.{ ExecutionContext, Future }

/** Converters between Protobuf and Mantik elements. */
private[services] object Converters {

  def encodeMantikItem(mantikItem: MantikItem): ProtoMantikItem = {
    mantikItem match {
      case a: Algorithm => ProtoMantikItem(
        kind = ObjectKind.KIND_ALGORITHM,
        item = Item.Algorithm(
          ProtoAlgorithm(a.stack)
        ))
      case d: DataSet => ProtoMantikItem(
        kind = ObjectKind.KIND_DATASET,
        item = Item.Dataset(
          ProtoDataSet(
            `type` = Some(ProtoDataType(d.dataType.asJson.noSpaces)),
            stack = d.stack
          )
        )
      )
      case t: TrainableAlgorithm => ProtoMantikItem(
        kind = ObjectKind.KIND_TRAINABLE_ALGORITHM,
        item = Item.TrainableAlgorithm(
          ProtoTrainableAlgorithm(
            stack = t.stack
          )
        )
      )
    }
  }

  /**
   * Decodes a Bundle.
   * TODO: Performance is bad, as this is done async, but doesn't need to be.
   */
  def decodeBundle(protoBundle: ProtoBundle)(implicit ec: ExecutionContext, materializer: Materializer): Future[Bundle] = {
    val dataType = protoBundle.dataType.map(decodeDataType).getOrElse {
      throw new IllegalArgumentException("Missing Datatype")
    }
    protoBundle.encoding match {
      case BundleEncoding.ENCODING_MSG_PACK =>
        val bytes = decodeByteString(protoBundle.encoded)
        val sink = Bundle.fromStreamWithoutHeader(dataType)
        Source.single(bytes).runWith(sink)
      case BundleEncoding.ENCODING_JSON =>
        val decoded = for {
          json <- parser.parse(protoBundle.encoded.toStringUtf8)
          decoded <- JsonFormat.deserializeBundleValue(dataType, json)
        } yield decoded
        decoded match {
          case Left(error) => Future.failed(
            new IllegalArgumentException("Could not decode json", error)
          )
          case Right(ok) => Future.successful(
            ok
          )
        }
      case other =>
        throw new IllegalArgumentException(s"Unknown encoding ${other}")
    }
  }

  /**
   * Encode a Bundle
   * TODO: Performance is bad, as this is done async, but doesn't need to be.
   */
  def encodeBundle(bundle: Bundle, encoding: BundleEncoding)(implicit ec: ExecutionContext, materializer: Materializer): Future[ProtoBundle] = {
    encoding match {
      case BundleEncoding.ENCODING_MSG_PACK =>
        encodeBundleMsgPack(bundle)
      case BundleEncoding.ENCODING_JSON =>
        val dataTypeString = bundle.model.toJsonString
        val jsonValue = JsonFormat.serializeBundleValue(bundle).noSpaces
        Future.successful(
          ProtoBundle(
            Some(ProtoDataType(dataTypeString)),
            encoding = BundleEncoding.ENCODING_JSON,
            encoded = ProtoByteString.copyFrom(jsonValue, StandardCharsets.UTF_8)
          )
        )
      case other =>
        throw new IllegalArgumentException(s"Unknown encoding ${other}")
    }
  }

  private def encodeBundleMsgPack(bundle: Bundle)(implicit ec: ExecutionContext, materializer: Materializer): Future[ProtoBundle] = {
    bundle.encode(false).runWith(Sink.seq).map { byteBlobs =>
      val dataTypeString = bundle.model.toJsonString
      ProtoBundle(
        Some(ProtoDataType(dataTypeString)),
        encoding = BundleEncoding.ENCODING_MSG_PACK,
        encoded = encodeByteString(byteBlobs)
      )
    }
  }

  def decodeDataType(dataType: ProtoDataType): DataType = {
    val decoded = for {
      parsed <- parser.parse(dataType.json)
      decoded <- parsed.as[DataType]
    } yield decoded
    decoded match {
      case Left(error) => throw new IllegalArgumentException(s"Could not parse data type ${error}")
      case Right(ok)   => ok
    }
  }

  def decodeByteString(bs: ProtoByteString): ByteString = {
    ByteString.fromArrayUnsafe(bs.toByteArray)
  }

  def encodeByteString(bs: ByteString): ProtoByteString = {
    bs.asByteBuffers.foldLeft(ProtoByteString.EMPTY) {
      case (c, n) =>
        c.concat(ProtoByteString.copyFrom(n))
    }
  }

  def encodeByteString(bs: IterableOnce[ByteString]): ProtoByteString = {
    var result = ProtoByteString.EMPTY
    for {
      b <- bs
      p <- b.asByteBuffers
    } {
      result = result.concat(ProtoByteString.copyFrom(p))
    }
    result
  }

}
