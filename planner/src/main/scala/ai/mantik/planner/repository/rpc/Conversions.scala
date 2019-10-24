package ai.mantik.planner.repository.rpc

import java.time.Instant

import ai.mantik.componently.rpc.RpcConversions
import ai.mantik.ds.helper.circe.CirceJson
import ai.mantik.elements.{ ItemId, MantikId, Mantikfile, NamedMantikId }
import ai.mantik.planner.repository.{ DeploymentInfo, Errors, MantikArtifact }
import ai.mantik.planner.repository.protos.types.{ MantikArtifact => ProtoMantikArtifact }
import ai.mantik.planner.repository.protos.types.{ DeploymentInfo => ProtoDeploymentInfo }
import ai.mantik.componently.utils.EitherExtensions._
import io.grpc.Status.Code
import io.grpc.{ Status, StatusRuntimeException }

import scala.concurrent.{ ExecutionContext, Future }

private[rpc] object Conversions {

  def encodeMantikId(mantikId: MantikId): String = {
    mantikId.toString
  }

  def decodeMantikId(str: String): MantikId = {
    MantikId.fromString(str)
  }

  def encodeNamedMantikId(namedMantikId: NamedMantikId): String = {
    namedMantikId.toString
  }

  def decodeNamedMantikId(str: String): NamedMantikId = {
    NamedMantikId.fromString(str)
  }

  def encodeItemId(itemId: ItemId): String = {
    itemId.toString
  }

  def decodeItemId(str: String): ItemId = {
    ItemId.fromString(str)
  }

  def encodeMantikArtifact(item: MantikArtifact): ProtoMantikArtifact = {
    ProtoMantikArtifact(
      mantikfile = item.mantikfile,
      fileId = RpcConversions.encodeOptionalString(item.fileId),
      mantikId = RpcConversions.encodeOptionalString(item.namedId.map(encodeMantikId)),
      itemId = encodeItemId(item.itemId),
      deploymentInfo = item.deploymentInfo.map(encodeDeploymentInfo)
    )
  }

  def decodeMantikArtifact(item: ProtoMantikArtifact): MantikArtifact = {
    MantikArtifact(
      mantikfile = item.mantikfile,
      fileId = RpcConversions.decodeOptionalString(item.fileId),
      namedId = RpcConversions.decodeOptionalString(item.mantikId).map(Conversions.decodeNamedMantikId),
      itemId = decodeItemId(item.itemId),
      deploymentInfo = item.deploymentInfo.map(decodeDeploymentInfo)
    )
  }

  def encodeDeploymentInfo(item: DeploymentInfo): ProtoDeploymentInfo = {
    ProtoDeploymentInfo(
      name = item.name,
      internalUrl = item.internalUrl,
      externalUrl = RpcConversions.encodeOptionalString(item.externalUrl),
      timestamp = item.timestamp.toEpochMilli
    )
  }

  def decodeDeploymentInfo(deploymentInfo: ProtoDeploymentInfo): DeploymentInfo = {
    DeploymentInfo(
      name = deploymentInfo.name,
      internalUrl = deploymentInfo.internalUrl,
      externalUrl = RpcConversions.decodeOptionalString(deploymentInfo.externalUrl),
      timestamp = Instant.ofEpochMilli(deploymentInfo.timestamp)
    )
  }

  val encodeErrors: PartialFunction[Throwable, Throwable] = {
    case e: Errors.NotFoundException =>
      RpcConversions.encodeError(e, Code.NOT_FOUND)
    case e: Errors.ConflictException =>
      RpcConversions.encodeError(e, Code.FAILED_PRECONDITION)
  }

  def encodeErrorIfPossible(e: Throwable): Throwable = {
    if (encodeErrors.isDefinedAt(e)) {
      encodeErrors.apply(e)
    } else {
      e
    }
  }

  def encodeErrorsIn[T](f: => Future[T])(implicit ec: ExecutionContext): Future[T] = {
    f.recover {
      case e if encodeErrors.isDefinedAt(e) =>
        throw encodeErrors(e)
    }
  }

  val decodeErrors: PartialFunction[Throwable, Throwable] = {
    case e: StatusRuntimeException if e.getStatus.getCode == Code.NOT_FOUND =>
      new Errors.NotFoundException(e.getStatus.getDescription)
    case e: StatusRuntimeException if e.getStatus.getCode == Code.FAILED_PRECONDITION =>
      // TODO: This is hack
      new Errors.ConflictException(e.getStatus.getDescription)
  }

  def decodeErrorsIn[T](f: => Future[T])(implicit ec: ExecutionContext): Future[T] = {
    f.recover {
      case e if decodeErrors.isDefinedAt(e) =>
        throw decodeErrors(e)
    }
  }
}
