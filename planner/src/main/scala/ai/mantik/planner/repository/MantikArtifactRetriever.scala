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
package ai.mantik.planner.repository

import java.nio.file.Path

import ai.mantik.componently.Component
import ai.mantik.elements.{MantikId, NamedMantikId}
import ai.mantik.planner.repository.impl.MantikArtifactRetrieverImpl
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.google.inject.ImplementedBy

import scala.concurrent.Future

/** Responsible for retrieving [[MantikArtifact]] from local repository and remote registry. */
@ImplementedBy(classOf[MantikArtifactRetrieverImpl])
trait MantikArtifactRetriever extends Component {

  /** Pull an Item from external Registry and put it into the local repository. */
  def pull(id: MantikId, customLoginToken: Option[CustomLoginToken] = None): Future[MantikArtifactWithHull]

  /** Tries to load an item from local repository, and if not available from a remote repository. */
  def get(id: MantikId): Future[MantikArtifactWithHull]

  /** Load the hull of many mantik Ids */
  def getHull(many: Seq[MantikId]): Future[Seq[MantikArtifact]]

  /** Loads an item locally. */
  def getLocal(id: MantikId): Future[MantikArtifactWithHull]

  /** Pushes an Item from the local repository to the remote registry. */
  def push(id: MantikId, customLoginToken: Option[CustomLoginToken] = None): Future[MantikArtifactWithHull]

  /** Add a local directory to the local repository. */
  def addLocalMantikItemToRepository(dir: Path, id: Option[NamedMantikId] = None): Future[MantikArtifact]

  /** Add a mantikheader / file stream to a local repository (more raw way) */
  def addMantikItemToRepository(
      mantikHeader: String,
      id: Option[NamedMantikId],
      payload: Option[(String, Source[ByteString, _])]
  ): Future[MantikArtifact]
}
