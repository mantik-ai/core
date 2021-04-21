package ai.mantik.engine.server.services

import java.nio.charset.StandardCharsets
import java.time.Instant

import ai.mantik.componently.rpc.RpcConversions
import ai.mantik.ds.DataType
import ai.mantik.ds.element.Bundle
import ai.mantik.ds.formats.json.JsonFormat
import ai.mantik.ds.helper.circe.CirceJson
import ai.mantik.elements.{ItemId, MantikHeader, NamedMantikId}
import ai.mantik.engine.protos.items.MantikItem.Item
import ai.mantik.engine.protos.items.ObjectKind
import ai.mantik.planner.{Algorithm, DataSet, MantikItem, Pipeline, TrainableAlgorithm}
import ai.mantik.engine.protos.items.{ObjectKind, MantikItem => ProtoMantikItem}
import ai.mantik.engine.protos.items.{Algorithm => ProtoAlgorithm}
import ai.mantik.engine.protos.items.{DataSet => ProtoDataSet}
import ai.mantik.engine.protos.items.{Pipeline => ProtoPipeline}
import ai.mantik.engine.protos.items.{Bridge => ProtoBridge}
import ai.mantik.engine.protos.items.{TrainableAlgorithm => ProtoTrainableAlgorithm}
import ai.mantik.engine.protos.ds.{BundleEncoding, Bundle => ProtoBundle, DataType => ProtoDataType}
import ai.mantik.engine.protos.registry.{
  DeploymentInfo => ProtoDeploymentInfo,
  MantikArtifact => ProtoMantikArtifact,
  SubDeploymentInfo => ProtoSubDeploymentInfo
}
import ai.mantik.planner.repository.{Bridge, DeploymentInfo, MantikArtifact, SubDeploymentInfo}
import com.google.protobuf.{ByteString => ProtoByteString}
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import akka.util.ccompat.IterableOnce
import com.google.protobuf.timestamp.Timestamp
import io.circe.syntax._
import io.circe.parser

import scala.concurrent.{ExecutionContext, Future}

/** Converters between Protobuf and Mantik elements. */
private[engine] object Converters {

  def encodeMantikItem(mantikItem: MantikItem): ProtoMantikItem = {
    val protoResponse = mantikItem match {
      case a: Algorithm =>
        ProtoMantikItem(
          kind = ObjectKind.KIND_ALGORITHM,
          item = Item.Algorithm(
            ProtoAlgorithm(
              a.bridgeMantikId.toString,
              inputType = Some(encodeDataType(a.functionType.input)),
              outputType = Some(encodeDataType(a.functionType.output))
            )
          )
        )
      case d: DataSet =>
        ProtoMantikItem(
          kind = ObjectKind.KIND_DATASET,
          item = Item.Dataset(
            ProtoDataSet(
              d.bridgeMantikId.toString,
              `type` = Some(ProtoDataType(d.dataType.asJson.noSpaces))
            )
          )
        )
      case t: TrainableAlgorithm =>
        ProtoMantikItem(
          kind = ObjectKind.KIND_TRAINABLE_ALGORITHM,
          item = Item.TrainableAlgorithm(
            ProtoTrainableAlgorithm(
              bridge = t.bridgeMantikId.toString,
              trainingType = Some(encodeDataType(t.trainingDataType)),
              statType = Some(encodeDataType(t.statType)),
              inputType = Some(encodeDataType(t.functionType.input)),
              outputType = Some(encodeDataType(t.functionType.output))
            )
          )
        )
      case p: Pipeline =>
        ProtoMantikItem(
          kind = ObjectKind.KIND_PIPELINE,
          item = Item.Pipeline(
            ProtoPipeline(
              inputType = Some(encodeDataType(p.functionType.input)),
              outputType = Some(encodeDataType(p.functionType.output))
            )
          )
        )
      case b: Bridge =>
        ProtoMantikItem(
          kind = ObjectKind.KIND_BRIDGE,
          item = Item.Bridge(
            ProtoBridge(
              dockerImage = b.mantikHeader.definition.dockerImage,
              suitable = b.mantikHeader.definition.suitable,
              protocol = b.mantikHeader.definition.protocol,
              payloadContentType = RpcConversions.encodeOptionalString(b.mantikHeader.definition.payloadContentType)
            )
          )
        )
    }
    protoResponse.copy(
      mantikHeaderJson = mantikItem.mantikHeader.toJson
    )
  }

  /**
    * Decodes a Bundle.
    */
  def decodeBundle(protoBundle: ProtoBundle)(implicit ec: ExecutionContext, materializer: Materializer): Bundle = {
    val dataType = protoBundle.dataType.map(decodeDataType).getOrElse {
      throw new IllegalArgumentException("Missing Datatype")
    }
    protoBundle.encoding match {
      case BundleEncoding.ENCODING_MSG_PACK =>
        val bytes = RpcConversions.decodeByteString(protoBundle.encoded)
        Bundle.fromByteStringWithoutHeader(dataType, bytes)
      case BundleEncoding.ENCODING_JSON =>
        val decoded = for {
          json <- parser.parse(protoBundle.encoded.toStringUtf8)
          decoded <- JsonFormat.deserializeBundleValue(dataType, json)
        } yield decoded
        decoded match {
          case Left(error) => throw new IllegalArgumentException("Could not decode json", error)
          case Right(ok)   => ok
        }
      case other =>
        throw new IllegalArgumentException(s"Unknown encoding ${other}")
    }
  }

  /**
    * Encode a Bundle
    */
  def encodeBundle(
      bundle: Bundle,
      encoding: BundleEncoding
  )(implicit ec: ExecutionContext, materializer: Materializer): ProtoBundle = {
    encoding match {
      case BundleEncoding.ENCODING_MSG_PACK =>
        encodeBundleMsgPack(bundle)
      case BundleEncoding.ENCODING_JSON =>
        val jsonValue = JsonFormat.serializeBundleValue(bundle).noSpaces
        ProtoBundle(
          Some(encodeDataType(bundle.model)),
          encoding = BundleEncoding.ENCODING_JSON,
          encoded = ProtoByteString.copyFrom(jsonValue, StandardCharsets.UTF_8)
        )
      case other =>
        throw new IllegalArgumentException(s"Unknown encoding ${other}")
    }
  }

  private def encodeBundleMsgPack(
      bundle: Bundle
  )(implicit ec: ExecutionContext, materializer: Materializer): ProtoBundle = {
    val bytes = bundle.encodeAsByteString(false)
    ProtoBundle(
      Some(encodeDataType(bundle.model)),
      encoding = BundleEncoding.ENCODING_MSG_PACK,
      encoded = RpcConversions.encodeByteString(bytes)
    )
  }

  def encodeDataType(dataType: DataType): ProtoDataType = {
    ProtoDataType(dataType.toJsonString)
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

  def encodeMantikArtifact(artifact: MantikArtifact): ProtoMantikArtifact = {
    ProtoMantikArtifact(
      mantikHeaderJson = artifact.parsedMantikHeader.toJson,
      artifactKind = artifact.parsedMantikHeader.definition.kind,
      fileId = RpcConversions.encodeOptionalString(artifact.fileId),
      namedId = RpcConversions.encodeOptionalString(artifact.namedId.map(_.toString)),
      itemId = artifact.itemId.toString,
      deploymentInfo = artifact.deploymentInfo.map(encodeDeploymentInfo),
      mantikHeader = artifact.mantikHeader
    )
  }

  def decodeMantikArtifact(artifact: ProtoMantikArtifact): MantikArtifact = {
    val originalMantikHeader = RpcConversions
      .decodeOptionalString(artifact.mantikHeader)
      .orElse(
        RpcConversions.decodeOptionalString(artifact.mantikHeaderJson)
      )
      .getOrElse(
        throw new IllegalArgumentException("Missing MantikHeader")
      )
    val result = MantikArtifact(
      mantikHeader = originalMantikHeader,
      fileId = RpcConversions.decodeOptionalString(artifact.fileId),
      namedId = RpcConversions.decodeOptionalString(artifact.namedId).map(NamedMantikId.fromString),
      itemId = ItemId(artifact.itemId),
      deploymentInfo = artifact.deploymentInfo.map(decodeDeploymentInfo)
    )
    result.parsedMantikHeader // force parsing
    result
  }

  def encodeDeploymentInfo(deploymentInfo: DeploymentInfo): ProtoDeploymentInfo = {
    ProtoDeploymentInfo(
      name = deploymentInfo.name,
      internalUrl = deploymentInfo.internalUrl,
      externalUrl = RpcConversions.encodeOptionalString(deploymentInfo.externalUrl),
      timestamp = Some(
        encodeInstantToScalaProto(deploymentInfo.timestamp)
      ),
      sub = deploymentInfo.sub.mapValues { s =>
        ProtoSubDeploymentInfo(
          name = s.name,
          internalUrl = s.internalUrl
        )
      }
    )
  }

  def decodeDeploymentInfo(deploymentInfo: ProtoDeploymentInfo): DeploymentInfo = {
    DeploymentInfo(
      name = deploymentInfo.name,
      internalUrl = deploymentInfo.internalUrl,
      externalUrl = RpcConversions.decodeOptionalString(deploymentInfo.externalUrl),
      timestamp = decodeInstantFromScalaProto(
        deploymentInfo.timestamp.getOrElse(
          throw new IllegalArgumentException("Expected timestamp")
        )
      ),
      sub = deploymentInfo.sub.mapValues { s =>
        SubDeploymentInfo(
          name = s.name,
          internalUrl = s.internalUrl
        )
      }
    )
  }

  def encodeInstantToScalaProto(i: Instant): Timestamp = {
    Timestamp.fromJavaProto(
      RpcConversions.encodeInstant(i)
    )
  }

  def decodeInstantFromScalaProto(t: Timestamp): Instant = {
    RpcConversions.decodeInstant(Timestamp.toJavaProto(t))
  }
}
