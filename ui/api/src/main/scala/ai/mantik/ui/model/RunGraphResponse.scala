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
package ai.mantik.ui.model

import ai.mantik.ds.helper.circe.DiscriminatorDependentCodec
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.JsonCodec

/** Evaluation graph. */
@JsonCodec
case class RunGraphResponse(
    graph: RunGraph,
    version: Long
)

/** Node in a run graph response. */
sealed trait RunGraphNode

object RunGraphNode {

  case class FileNode(
      fileRef: Int,
      contentType: String
  ) extends RunGraphNode

  case class MnpNode(
      image: String,
      parameters: Seq[String],
      mantikHeader: Json,
      // Content type of payload file
      payloadFileContentType: Option[String],
      embeddedPayloadSize: Option[Long]
  ) extends RunGraphNode

  implicit val operationCodec: Encoder[RunGraphNode] with Decoder[RunGraphNode] =
    new DiscriminatorDependentCodec[RunGraphNode]("type") {
      val subTypes = Seq(
        makeSubType[FileNode]("file"),
        makeSubType[MnpNode]("mnp")
      )
    }
}

/** Link in a run graph response. */
@JsonCodec
case class RunGraphLink(
    fromPort: Int,
    toPort: Int,
    contentType: String,
    bytes: Option[Long] = None
)
