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
package ai.mantik.planner.util

import ai.mantik.elements.{BridgeDefinition, ItemId, MantikDefinition, MantikHeader, NamedMantikId}
import ai.mantik.planner.DefinitionSource
import ai.mantik.planner.repository.{Bridge, ContentTypes}

trait FakeBridges {

  val algoBridge = Bridge(
    DefinitionSource.Loaded(Some(NamedMantikId("algo1")), ItemId("@id1")),
    MantikHeader.pure(
      BridgeDefinition(
        dockerImage = "docker_algo1",
        suitable = Seq(MantikDefinition.AlgorithmKind),
        protocol = 1,
        payloadContentType = Some(ContentTypes.ZipFileContentType)
      )
    )
  )

  val learningBridge = Bridge(
    DefinitionSource.Loaded(Some(NamedMantikId("training1")), ItemId("@id2")),
    MantikHeader.pure(
      BridgeDefinition(
        dockerImage = "docker_training1",
        suitable = Seq(MantikDefinition.AlgorithmKind, MantikDefinition.TrainableAlgorithmKind),
        protocol = 1,
        payloadContentType = Some(ContentTypes.ZipFileContentType)
      )
    )
  )

  val formatBridge = Bridge(
    DefinitionSource.Loaded(Some(NamedMantikId("format1")), ItemId("@id3")),
    MantikHeader.pure(
      BridgeDefinition(
        dockerImage = "docker_format1",
        suitable = Seq(MantikDefinition.AlgorithmKind),
        protocol = 1,
        payloadContentType = Some(ContentTypes.ZipFileContentType)
      )
    )
  )
}
