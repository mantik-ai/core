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

import java.time.Instant
import ai.mantik.elements.errors.InvalidMantikHeaderException
import ai.mantik.elements.{ItemId, MantikDefinition, MantikHeader, MantikId, NamedMantikId}
import ai.mantik.mnp.MnpSessionUrl

/**
  * A Mantik Artefact.
  * @param mantikHeader the MantikHeader.
  * @param fileId associated file.
  * @param namedId an optional name, if it's not an anonymous item.
  * @param itemId the itemId
  * @param deploymentInfo optional current deployment info.
  * @param executorStorageId id of the payload mirrored in executor storage
  */
case class MantikArtifact(
    mantikHeader: String,
    fileId: Option[String],
    namedId: Option[NamedMantikId],
    itemId: ItemId,
    deploymentInfo: Option[DeploymentInfo] = None,
    executorStorageId: Option[String] = None
) {

  /** Returns the named Mantik Id if given, or the itemId as fallback */
  def mantikId: MantikId = namedId.getOrElse(itemId)

  /** The parsed MantikHeader. */
  lazy val parsedMantikHeader: MantikHeader[_ <: MantikDefinition] =
    MantikHeader.fromYaml(mantikHeader).fold(e => throw InvalidMantikHeaderException.wrap(e), identity)
}

object MantikArtifact {

  /** Build an Artifact from a MantikDefinifion */
  def makeFromDefinition[T <: MantikDefinition](
      mantikDefinition: MantikDefinition,
      name: NamedMantikId,
      fileId: Option[String] = None
  ): MantikArtifact = {
    MantikArtifact(
      mantikHeader = MantikHeader.pure(mantikDefinition).toJson,
      fileId = fileId,
      namedId = Some(name),
      itemId = ItemId.generate(),
      deploymentInfo = None
    )
  }
}

/**
  * Deployment Information as being stored in the [[Repository]].
  *
  * @param evaluationId of the node in the Executor
  * @param internalUrl MNP Url under which its reachable.
  * @param externalUrl optional external HTTP Url
  * @param timestamp timestamp
  */
case class DeploymentInfo(
    evaluationId: String,
    internalUrl: String,
    externalUrl: Option[String] = None,
    timestamp: Instant
)

/** Sub Part deployment infos (e.g. for embedded SQL Nodes) */
case class SubDeploymentInfo(
    name: String,
    internalUrl: String
)
