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
import ai.mantik.executor.docker.api.InstantCodec._

import io.circe.generic.JsonCodec

@JsonCodec
case class ListContainerRequestFilter(
    label: Vector[String]
)

object ListContainerRequestFilter {

  /**
    * Build a filter for a given label key and value.
    * For more information see Docker API specification.
    */
  def forLabelKeyValue(keyValues: (String, String)*): ListContainerRequestFilter = {
    val labels = keyValues.map { case (key, value) =>
      s"$key=$value"
    }.toVector
    ListContainerRequestFilter(
      label = labels
    )
  }
}

/** Single row on a list container field. */
@JsonCodec
case class ListContainerResponseRow(
    Id: String,
    Image: String,
    Command: Option[String],
    Names: Vector[String] = Vector.empty,
    Labels: Map[String, String] = Map.empty,
    State: String,
    // Human readable status code
    Status: Option[String] = None
)
