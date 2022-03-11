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

import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.elements.errors.{ErrorCodes, MantikException}
import ai.mantik.elements.{ItemId, MantikId, NamedMantikId}
import ai.mantik.planner.BuiltInItems
import ai.mantik.planner.repository.{FileRepository, LocalMantikRegistry, MantikArtifact, Repository}
import akka.stream.scaladsl.Source
import akka.util.ByteString

import javax.inject.Inject
import scala.concurrent.Future

class LocalMantikRegistryImpl @Inject() (
    fileRepository: FileRepository,
    repository: Repository
)(
    implicit akkaRuntime: AkkaRuntime
) extends ComponentBase
    with LocalMantikRegistry {

  override def get(mantikId: MantikId): Future[MantikArtifact] = {
    getBuiltIn(mantikId).map(Future.successful).getOrElse {
      repository.get(mantikId)
    }
  }

  /** Resolve a reference to a built in item, if it exists */
  private def getBuiltIn(mantikId: MantikId): Option[MantikArtifact] = {
    BuiltInItems.readBuiltInItem(mantikId).map { item =>
      MantikArtifact(
        mantikHeader = item.mantikHeader.toJson,
        fileId = None,
        namedId = item.source.definition.name,
        itemId = item.itemId,
        deploymentInfo = None,
        executorStorageId = None
      )
    }
  }

  override def getPayload(fileId: String): Future[(String, Source[ByteString, _])] = {
    fileRepository
      .loadFile(fileId)
      .recover {
        case e: MantikException if e.code.isA(FileRepository.NotFoundCode) =>
          ErrorCodes.MantikItemPayloadNotFound.throwIt(s"Payload ${fileId} not found", e)
      }
      .map { result =>
        (result.contentType, result.source)
      }
  }

  override def addMantikArtifact(
      mantikArtifact: MantikArtifact,
      payload: Option[(String, Source[ByteString, _])]
  ): Future[MantikArtifact] = {
    val fileStorage: Future[Option[(String, Future[Long])]] = payload
      .map { case (contentType, source) =>
        for {
          storage <- fileRepository.requestFileStorage(contentType, temporary = false)
          sink <- fileRepository.storeFile(storage.fileId)
        } yield {
          Some(storage.fileId -> source.runWith(sink))
        }
      }
      .getOrElse(
        Future.successful(None)
      )

    for {
      storage <- fileStorage
      updated = mantikArtifact.copy(fileId = storage.map(_._1))
      _ <- repository.store(updated)
      _ <- storage.map(_._2).getOrElse(Future.successful(())) // wait for file upload
    } yield {
      updated
    }
  }

  override def ensureMantikId(itemId: ItemId, mantikId: NamedMantikId): Future[Boolean] = {
    repository.ensureMantikId(itemId, mantikId)
  }

  override def list(
      alsoAnonymous: Boolean,
      deployedOnly: Boolean,
      kindFilter: Option[String]
  ): Future[IndexedSeq[MantikArtifact]] = {
    repository.list(
      alsoAnonymous = alsoAnonymous,
      deployedOnly = deployedOnly,
      kindFilter = kindFilter
    )
  }
}
