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

import io.circe.{Decoder, Encoder, Json}

import scala.util.Try
import scala.util.control.NonFatal

/** Provides an encoding for an operation. On java script side this is just a string
  * @param value either a special id or a coordinate set
  */
case class OperationId(
    value: Either[String, List[Int]]
) {
  override def toString: String = {
    value match {
      case Left(value)  => OperationId.stringPrefix + value
      case Right(value) => value.mkString(",")
    }
  }
}

object OperationId {
  val stringPrefix = "k"
  def fromString(s: String): Try[OperationId] = {
    Try {
      if (s.startsWith(stringPrefix)) {
        OperationId(Left(s.stripPrefix(stringPrefix)))
      } else {
        val coordinates = s.split(',').map(_.toInt).toList
        OperationId(Right(coordinates))
      }
    }
  }

  implicit val encoder: Encoder[OperationId] = Encoder.encodeString.contramap { o: OperationId => o.toString }
  implicit val decoder: Decoder[OperationId] = Decoder.decodeString.emapTry(OperationId.fromString)
}
