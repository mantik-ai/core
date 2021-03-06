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
package ai.mantik.planner.repository.impl

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import ai.mantik.componently.utils.ConfigExtensions._
import ai.mantik.componently.utils.FutureHelper
import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.ds.helper.ZipUtils
import ai.mantik.elements.errors.{ErrorCodes, MantikException}
import ai.mantik.elements.{
  BridgeDefinition,
  ItemId,
  MantikDefinition,
  MantikDefinitionWithBridge,
  MantikDefinitionWithoutBridge,
  MantikHeader,
  MantikId,
  NamedMantikId
}
import ai.mantik.planner.BuiltInItems
import ai.mantik.planner.impl.ReferencingItemLoader
import ai.mantik.planner.repository._
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString

import javax.inject.{Inject, Singleton}
import org.apache.commons.io.FileUtils
import cats.implicits._

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

/** Responsible for pulling/pushing Mantik Artifacts from local repository and remote registry. */
@Singleton
private[mantik] class MantikArtifactRetrieverImpl @Inject() (
    localMantikRegistry: LocalMantikRegistry,
    defaultRemoteRegistry: RemoteMantikRegistry
)(implicit akkaRuntime: AkkaRuntime)
    extends ComponentBase
    with MantikArtifactRetriever {

  private val dbLookupTimeout = config.getFiniteDuration("mantik.planner.dbLookupTimeout")
  private val registryTimeout = config.getFiniteDuration("mantik.planner.registryTimeout")
  private val fileTransferTimeout = config.getFiniteDuration("mantik.planner.fileTransferTimeout")

  /** ReferencingItemLoader for MantikArtifacts. */
  private class ReferencingMantikArtifactLoader(loader: MantikId => Future[MantikArtifact])
      extends ReferencingItemLoader[MantikId, MantikArtifact](
        loader,
        item => dependencyExtractor(item.parsedMantikHeader)
      )

  /** Figures out dependencies to load from Artifacts, skips Built Ins. */
  private def dependencyExtractor(header: MantikHeader[_ <: MantikDefinition]): Seq[MantikId] = {
    header.definition.referencedItems.filter {
      case n: NamedMantikId => n.account != BuiltInItems.BuiltInAccount
      case _                => true
    }
  }

  private val repositoryLoader = new ReferencingMantikArtifactLoader(localRepoGet)

  private val localOrRemoteLoader = new ReferencingMantikArtifactLoader(localOrRemoteGet)

  private def localRepoGet(mantikId: MantikId): Future[MantikArtifact] =
    FutureHelper.addTimeout(
      localMantikRegistry.get(mantikId),
      "Loading MantikHeader from local repository",
      dbLookupTimeout
    )

  private def localOrRemoteGet(mantikId: MantikId): Future[MantikArtifact] = {
    localRepoGet(mantikId).recoverWith {
      case n: MantikException if n.code.isA(ErrorCodes.MantikItemNotFound) =>
        logger.info(s"${mantikId} not available locally, pulling...")
        pull(mantikId).map(_._1)
    }
  }

  override def pull(id: MantikId, customLoginToken: Option[CustomLoginToken] = None): Future[MantikArtifactWithHull] = {
    logger.info(s"Pulling ${id}")
    val remoteRegistry = wrapRemoteRegistry(customLoginToken)
    val loader = new ReferencingMantikArtifactLoader(wrapRemoteGet(remoteRegistry))

    for {
      items <- loader.loadWithHull(id)
      local <- pullRemoteItemsToLocal(remoteRegistry, items)
    } yield (
      (local.head -> local.tail)
    )
  }

  private def wrapRemoteGet(registry: MantikRegistry): MantikId => Future[MantikArtifact] = { mantikId =>
    FutureHelper.addTimeout(registry.get(mantikId), "Loading MantikHeader from remote Registry", registryTimeout)
  }

  private def wrapRemoteRegistry(customLoginToken: Option[CustomLoginToken]): MantikRegistry = {
    customLoginToken
      .map { token =>
        defaultRemoteRegistry.withCustomToken(token)
      }
      .getOrElse {
        defaultRemoteRegistry
      }
  }

  override def get(id: MantikId): Future[MantikArtifactWithHull] = {
    localOrRemoteLoader.loadWithHull(id).map { items =>
      items.head -> items.tail
    }
  }

  override def getHull(many: Seq[MantikId]): Future[Seq[MantikArtifact]] = {
    localOrRemoteLoader.loadHull(many)
  }

  override def getLocal(id: MantikId): Future[MantikArtifactWithHull] = {
    repositoryLoader.loadWithHull(id).map { items =>
      items.head -> items.tail
    }
  }

  override def push(id: MantikId, customLoginToken: Option[CustomLoginToken] = None): Future[MantikArtifactWithHull] = {
    val remoteRegistry = wrapRemoteRegistry(customLoginToken)
    for {
      items <- repositoryLoader.loadWithHull(id)
      remote <- pushLocalItemsToRemote(remoteRegistry, items)
    } yield {
      remote.head -> remote.tail
    }
  }

  override def addLocalMantikItemToRepository(dir: Path, id: Option[NamedMantikId] = None): Future[MantikArtifact] = {
    logger.info(s"Adding local Directory ${dir} with MantikHeader")
    val file = dir.resolve("MantikHeader")
    val mantikHeaderContent = FileUtils.readFileToString(file.toFile, StandardCharsets.UTF_8)

    val parsed = MantikHeader.fromYaml(mantikHeaderContent) match {
      case Left(value)  => return Future.failed(value)
      case Right(value) => value
    }

    payloadContentType(parsed).flatMap { expectedContentType =>
      logger.debug(s"Expected content type for ${dir}: ${expectedContentType}")
      // Note: some bridges have no expected content type, where we expect zip.
      val contentType = expectedContentType.getOrElse(ContentTypes.ZipFileContentType)

      val payloadFile = dir.resolve("payload")
      val payloadSource: Option[(String, Source[ByteString, _])] = contentType match {
        case ContentTypes.ZipFileContentType if Files.isDirectory(payloadFile) =>
          logger.debug(s"Compressing directory ${payloadFile}")
          val tempFile = Files.createTempFile("mantik_context", ".zip")
          ZipUtils.zipDirectory(payloadFile, tempFile)
          val source = FileIO
            .fromPath(tempFile)
            .mapMaterializedValue(_.andThen { _ =>
              tempFile.toFile.delete()
            })
          Some(ContentTypes.ZipFileContentType -> source)
        case _ if Files.isDirectory(payloadFile) =>
          throw new IllegalArgumentException(
            s"Expected content type ${contentType} but found directory in ${payloadFile}"
          )
        case ct if Files.isRegularFile(payloadFile) =>
          logger.debug(s"Handling ${payloadFile} as ${ct}")
          val source = FileIO.fromPath(payloadFile)
          Some(ct -> source)
        case _ =>
          None
      }

      addMantikItemToRepository(mantikHeaderContent, id, payloadSource)
    }
  }

  /** Figures out expected content type for a header */
  private def payloadContentType(mantikHeader: MantikHeader[_ <: MantikDefinition]): Future[Option[String]] = {
    val bridgeId = mantikHeader.definition match {
      case bridge: MantikDefinitionWithBridge => bridge.bridge
      case _: MantikDefinitionWithoutBridge   =>
        // Only items with bridges do have payload
        return Future.successful(None)
    }
    for {
      bridgeArtifact <- localOrRemoteGet(bridgeId)
      casted <- bridgeArtifact.parsedMantikHeader.cast[BridgeDefinition].fold(Future.failed, Future.successful)
    } yield Some(casted.definition.assumedContentType)
  }

  override def addMantikItemToRepository(
      mantikHeaderContent: String,
      id: Option[NamedMantikId],
      payload: Option[(String, Source[ByteString, _])]
  ): Future[MantikArtifact] = {
    // Parsing
    val mantikHeader = MantikHeader.fromYaml(mantikHeaderContent) match {
      case Left(error) => throw error
      case Right(ok)   => ok
    }

    val mantikId = id.orElse(mantikHeader.header.id)
    val itemId = ItemId.generate()
    ensureDependencies(mantikId.getOrElse(itemId), mantikHeader).flatMap { _ =>
      val artifact = MantikArtifact(mantikHeaderContent, None, mantikId, itemId)
      val timeout = if (payload.isDefined) {
        fileTransferTimeout
      } else {
        dbLookupTimeout
      }
      FutureHelper
        .addTimeout(
          localMantikRegistry.addMantikArtifact(artifact, payload),
          "Uploading Artifact",
          timeout
        )
        .map { generatedArtifact =>
          logger.info(s"Stored ${artifact.itemId} done, name=${artifact.namedId}, fileId=${artifact.fileId}")
          generatedArtifact
        }
    }
  }

  private def ensureDependencies(id: MantikId, mantikHeader: MantikHeader[_ <: MantikDefinition]): Future[Unit] = {
    logger.debug(s"Ensuring Dependencies of ${id}")
    val references = dependencyExtractor(mantikHeader)
    val futures = references.map { referenceId =>
      logger.debug(s"Ensuring dependency ${referenceId} of ${id}")
      get(referenceId)
    }
    Future.sequence(futures).map(_ => ())
  }

  private def pullRemoteItemsToLocal(
      remoteRepo: MantikRegistry,
      remote: Seq[MantikArtifact]
  ): Future[Seq[MantikArtifact]] = {
    Future.sequence(remote.map(pullRemoteItemToLocal(remoteRepo, _)))
  }

  private def pullRemoteItemToLocal(remoteRegistry: MantikRegistry, remote: MantikArtifact): Future[MantikArtifact] = {
    copyItem(
      "Pulling",
      remote,
      remoteRegistry,
      localMantikRegistry,
      fileTransferTimeout,
      dbLookupTimeout
    )
  }

  /** Copy an item to the `to` Registry. */
  private def copyItem(
      operationName: String,
      fromArtifact: MantikArtifact,
      from: MantikRegistry,
      to: MantikRegistry,
      fileTransferTimeout: FiniteDuration,
      changeTimeout: FiniteDuration
  ): Future[MantikArtifact] = {
    logger.debug(s"${operationName} ${fromArtifact.mantikId}")

    val existing = to.maybeGet(fromArtifact.itemId)

    existing.flatMap {
      case Some(existant) =>
        fromArtifact.namedId match {
          case Some(namedId) =>
            logger.info(s"${fromArtifact.itemId} already exists, ensuring id $namedId")
            FutureHelper
              .addTimeout(
                to.ensureMantikId(existant.itemId, namedId),
                "Tagging",
                changeTimeout
              )
              .map { _ =>
                existant.copy(namedId = Some(namedId))
              }
          case None =>
            logger.info(s"${fromArtifact.itemId} already exists, and is anonymous, no change.")
            Future.successful(existant)
        }
      case None =>
        logger.info(s"${fromArtifact.itemId} doesn't exist yet, copying entirely")
        val timeout = if (fromArtifact.fileId.isDefined) {
          fileTransferTimeout
        } else {
          changeTimeout
        }

        for {
          source <- FutureHelper.addTimeout(fromArtifact.fileId.map(from.getPayload).sequence, "Getting File", timeout)
          localArtifact <- FutureHelper.addTimeout(
            to.addMantikArtifact(fromArtifact, source),
            "Storing Artifact",
            timeout
          )
        } yield localArtifact
    }
  }

  /**
    * Push multiple local items to remote.
    * Local must be ordered (dependencies last)
    */
  private def pushLocalItemsToRemote(
      remoteRegistry: MantikRegistry,
      local: Seq[MantikArtifact]
  ): Future[Seq[MantikArtifact]] = {
    // Remote may check for dependencies
    val reversed = local.reverse
    FutureHelper.afterEachOther(reversed)(pushLocalItemToRemote(remoteRegistry, _)).map(_.reverse)
  }

  /** Push a single local item to remote registry. */
  private def pushLocalItemToRemote(remoteRegistry: MantikRegistry, local: MantikArtifact): Future[MantikArtifact] = {
    copyItem(
      "pushing",
      local,
      localMantikRegistry,
      remoteRegistry,
      fileTransferTimeout,
      registryTimeout
    )
  }
}
