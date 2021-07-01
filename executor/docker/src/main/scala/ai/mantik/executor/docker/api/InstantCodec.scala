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
package ai.mantik.executor.docker.api

import io.circe.{Decoder, Encoder}

import java.time.Instant
import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder}
import scala.util.Try

/** Docker sometimes sends timezone information together with timestamps */
object InstantCodec {

  // Parses ISO8601 Instant strings as well as encoded Timezone
  // https://stackoverflow.com/a/40729994
  private val isoDateParser = new DateTimeFormatterBuilder()
    .parseCaseInsensitive()
    .append(DateTimeFormatter.ISO_LOCAL_DATE)
    .optionalStart()
    .appendLiteral('T')
    .append(DateTimeFormatter.ISO_TIME)
    .toFormatter();

  // Sent as Plain to string
  implicit val encoder: Encoder[Instant] = Encoder.encodeString.contramap(_.toString)

  // Decode multiple ways
  implicit val decoder: Decoder[Instant] = Decoder.decodeString.emapTry { s =>
    Try {
      Instant.from(isoDateParser.parse(s))
    }
  }
}
