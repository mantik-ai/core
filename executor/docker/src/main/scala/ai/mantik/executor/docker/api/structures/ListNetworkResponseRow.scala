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
case class ListNetworkRequestFilter(
    // Note: much more fields possible
    name: Option[Vector[String]] = None,
    label: Option[Vector[String]] = None // In form label=value
)

object ListNetworkRequestFilter {
  def forLabels(labels: (String, String)*): ListNetworkRequestFilter = {
    ListNetworkRequestFilter(
      label = Some(
        labels.map { case (k, v) => s"${k}=${v}" }.toVector
      )
    )
  }
}

@JsonCodec
case class ListNetworkResponseRow(
    Name: String,
    Id: String,
    Created: Instant,
    Driver: String,
    Labels: Option[Map[String, String]] = None
) {
  def labels: Map[String, String] = Labels.getOrElse(Map.empty)
}
