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
package ai.mantik.executor.docker.api.structures

import java.time.Instant

import io.circe.generic.JsonCodec

@JsonCodec
case class InspectContainerResponse(
    Id: String,
    Created: Instant,
    Image: String,
    Args: List[String],
    NetworkSettings: InspectContainerNetworkResponse,
    State: InspectContainerStateResponse
)

@JsonCodec
case class InspectContainerNetworkResponse(
    Networks: Map[String, InspectContainerNetworkSpecificResponse] = Map.empty
)

@JsonCodec
case class InspectContainerStateResponse(
    Status: String,
    Running: Boolean,
    ExitCode: Option[Int]
)

@JsonCodec
case class InspectContainerNetworkSpecificResponse(
    NetworkID: String
)
