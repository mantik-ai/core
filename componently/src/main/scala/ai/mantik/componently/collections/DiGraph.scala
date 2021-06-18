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
package ai.mantik.componently.collections

import io.circe.generic.semiauto
import io.circe.{Decoder, Encoder, Json, KeyDecoder, KeyEncoder}

/**
  * Directed immutable Graph.
  *
  * @tparam K key for accessing nodes
  * @tparam N node data
  * @tparam L link data
  */
case class DiGraph[K, +N, +L](
    nodes: Map[K, N] = Map.empty[K, N],
    links: Vector[DiLink[K, L]] = Vector.empty
) {

  def merge[N2 >: N, L2 >: L](other: DiGraph[K, N2, L2]): DiGraph[K, N2, L2] = {
    DiGraph(
      nodes ++ other.nodes,
      links ++ other.links
    )
  }
}

/**
  * A Link inside [[DiGraph]]
  * @param from the id from which it is going
  * @param to the id to which it is going
  * @param data data attached to it.
  */
case class DiLink[K, +L](from: K, to: K, data: L)

object DiGraph {
  private implicit def keyEncoderToEncoder[T](implicit ke: KeyEncoder[T]): Encoder[T] = {
    Encoder.encodeString.contramap(ke.apply)
  }

  private implicit def keyDecoderToDecoder[T](implicit kd: KeyDecoder[T]): Decoder[T] = {
    Decoder.decodeString.emap { s =>
      kd(s) match {
        case None     => Left(s"Could not decode ${s}")
        case Some(ok) => Right(ok)
      }
    }

  }

  implicit def linkEncoder[K: KeyEncoder, L: Encoder]: Encoder[DiLink[K, L]] = semiauto.deriveEncoder
  implicit def encoder[K: KeyEncoder, N: Encoder, L: Encoder]: Encoder[DiGraph[K, N, L]] = semiauto.deriveEncoder
  implicit def linkDecoder[K: KeyDecoder, L: Decoder]: Decoder[DiLink[K, L]] = semiauto.deriveDecoder
  implicit def decoder[K: KeyDecoder, N: Decoder, L: Decoder]: Decoder[DiGraph[K, N, L]] = semiauto.deriveDecoder
}
