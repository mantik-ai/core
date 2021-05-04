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
package ai.mantik.elements.registry.api

import java.time.Instant

import ai.mantik.elements.{ItemId, NamedMantikId}
import io.circe.generic.JsonCodec

@JsonCodec
case class ApiGetArtifactResponse(
    // TODO: This is the MantikHeader, not the MantikDefinition
    mantikDefinition: String,
    mantikDefinitionJson: String, // as string so that it doesn't confuse golang's JSON unmarshal
    namedId: Option[NamedMantikId] = None,
    revision: Option[Int] = None,
    kind: String,
    creationTimestamp: Instant,
    uploadState: String,
    fileId: Option[String],
    itemId: ItemId
)

@JsonCodec
case class ApiPrepareUploadRequest(
    namedId: Option[NamedMantikId] = None,
    itemId: ItemId,
    mantikHeader: String,
    hasFile: Boolean
)

@JsonCodec
case class ApiTagRequest(
    itemId: ItemId,
    mantikId: NamedMantikId
)

@JsonCodec
case class ApiTagResponse(
    updated: Boolean // true if updated, false if already existant
)

@JsonCodec
case class ApiPrepareUploadResponse(
    revision: Option[Int] = None
)

@JsonCodec
case class ApiFileUploadResponse(
    fileId: String
)
