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
package ai.mantik.planner.csv

import io.circe.Codec
import io.circe.generic.semiauto

/**
  * Options for CSV Loading.
  *
  * Note: the JSON Representation is compatible with CSV Bridge
  *
  * @param comma comma character (default is ',')
  * @param skipHeader if true, the first line is skipped
  * @param comment comment character
  */
case class CsvOptions(
    comma: Option[String] = None,
    skipHeader: Option[Boolean] = None,
    comment: Option[String] = None
)

object CsvOptions {
  implicit val codec: Codec[CsvOptions] = semiauto.deriveCodec[CsvOptions]
}
