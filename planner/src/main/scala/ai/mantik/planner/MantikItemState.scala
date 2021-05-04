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
package ai.mantik.planner

import ai.mantik.elements.NamedMantikId
import io.circe.{Encoder, Decoder}
import io.circe.generic.semiauto

/**
  * The current run time state of a Mantik Item.
  *
  * @param namedMantikItem the mantik id if the Item is stored/loaded inside the repository.
  * @param itemStored the item itself is stored (this doesn't require that it has a mantik id)
  * @param nameStored the name is stored (this also requires that the item is stored).
  * @param deployment information about deployment
  * @param cacheFile temporary cache file holding the result
  */
case class MantikItemState(
    namedMantikItem: Option[NamedMantikId] = None,
    itemStored: Boolean = false,
    nameStored: Boolean = false,
    payloadFile: Option[String] = None,
    deployment: Option[DeploymentState] = None,
    cacheFile: Option[String] = None
)

object MantikItemState {

  /** Initialize a new MantikItemState from source. */
  def initializeFromSource(source: Source): MantikItemState = {
    val file = source.payload match {
      case PayloadSource.Loaded(fileId, _) => Some(fileId)
      case _                               => None
    }
    MantikItemState(
      namedMantikItem = source.definition.name,
      itemStored = source.definition.itemStored,
      nameStored = source.definition.nameStored,
      payloadFile = file
    )
  }

  implicit val encoder: Encoder[MantikItemState] = semiauto.deriveEncoder[MantikItemState]
  implicit val decoder: Decoder[MantikItemState] = semiauto.deriveDecoder[MantikItemState]
}

/** Deployment information of one [[MantikItem]]. */
case class DeploymentState(
    name: String,
    internalUrl: String,
    externalUrl: Option[String] = None,
    sub: Map[String, SubDeploymentState] = Map.empty
)

/** Sub Deployment state (e.g. Query Nodes) */
case class SubDeploymentState(
    name: String,
    internalUrl: String
)

object DeploymentState {
  implicit val subEncoder: Encoder[SubDeploymentState] = semiauto.deriveEncoder[SubDeploymentState]
  implicit val subDecoder: Decoder[SubDeploymentState] = semiauto.deriveDecoder[SubDeploymentState]
  implicit val encoder: Encoder[DeploymentState] = semiauto.deriveEncoder[DeploymentState]
  implicit val decoder: Decoder[DeploymentState] = semiauto.deriveDecoder[DeploymentState]
}
