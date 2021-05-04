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

import io.circe.generic.JsonCodec

@JsonCodec
case class CreateVolumeRequest(
    Name: String,
    Labels: Map[String, String] = Map.empty,
    Driver: String = "local"
)

@JsonCodec
case class CreateVolumeResponse(
    Name: String,
    Driver: String,
    Mountpoint: String,
    Status: Option[Map[String, String]] = None,
    Labels: Option[Map[String, String]] = None
)
