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
package ai.mantik.executor.model

import io.circe.generic.JsonCodec

/**
  * Request for stopping workers.
  * @param nameFilter if set, only remove workers of a given node name
  * @param idFilter if set, only remove workers of a given user id
  * @param remove if true, remove the workers completely
  */
@JsonCodec
case class StopWorkerRequest(
    nameFilter: Option[String] = None,
    idFilter: Option[String] = None,
    remove: StringWrapped[Boolean] = true
)

/** Response for stopping workers. */
@JsonCodec
case class StopWorkerResponse(
    removed: Seq[StopWorkerResponseElement]
)

/** Element for removing workers. */
@JsonCodec
case class StopWorkerResponseElement(
    id: String,
    name: String
)
